/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.analyst.event

import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.pojo.AutoScanConfiguration
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest
import com.tencent.bkrepo.analyst.pojo.rule.RuleArtifact
import com.tencent.bkrepo.analyst.pojo.rule.RuleArtifact.Companion.RULE_FIELD_LATEST_VERSION
import com.tencent.bkrepo.analyst.service.ProjectScanConfigurationService
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.service.SpdxLicenseService
import com.tencent.bkrepo.analyst.utils.RuleConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.constant.PUBLIC_GLOBAL_PROJECT
import com.tencent.bkrepo.common.artifact.constant.PUBLIC_VULDB_REPO
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.packages.VersionCreatedEvent
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.matcher.RuleMatcher
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.function.Consumer

/**
 * 构件事件消费者，用于触发制品更新扫描
 * 制品有新的推送时，筛选已开启自动扫描的方案进行扫描
 * 对应binding name为scanEventConsumer-in-0
 */
@Component
class ScanEventConsumer(
    private val spdxLicenseService: SpdxLicenseService,
    private val scanService: ScanService,
    private val scannerService: ScannerService,
    private val scanPlanDao: ScanPlanDao,
    private val projectScanConfigurationService: ProjectScanConfigurationService,
    private val executor: ThreadPoolTaskExecutor
) : Consumer<ArtifactEvent> {

    /**
     * 允许接收的事件类型
     */
    private val acceptTypes = setOf(
        EventType.NODE_CREATED,
        EventType.VERSION_CREATED,
        EventType.VERSION_UPDATED
    )

    override fun accept(event: ArtifactEvent) {
        if (!acceptTypes.contains(event.type)) {
            return
        }

        executor.execute {
            when (event.type) {
                EventType.NODE_CREATED -> {
                    scanOnNodeCreatedEvent(event)
                    importLicenseEvent(event)
                }
                EventType.VERSION_CREATED, EventType.VERSION_UPDATED -> scanOnVersionCreated(event)
                else -> throw UnsupportedOperationException()
            }
        }
    }

    /**
     * 当【public-global】项目下的【vuldb-repo】仓库中的【/spdx-license/】文件夹下上传license.json文件时
     * 触发导入license数据
     */
    private fun importLicenseEvent(event: ArtifactEvent) {
        if (event.projectId != PUBLIC_GLOBAL_PROJECT || event.repoName != PUBLIC_VULDB_REPO) {
            return
        }
        if (!event.resourceKey.endsWith(".json") || !event.resourceKey.startsWith("/spdx-license/")) {
            return
        }
        if (spdxLicenseService.importLicense(event.projectId, event.repoName, event.resourceKey)) {
            logger.info("import license json file success")
        } else {
            logger.warn("import license json file failed[$event]")
        }
    }

    /**
     * 当新制品上传时执行扫描
     *
     * @param event 新制品上传事件，NodeCreatedEvent只有Generic仓库会产生
     *
     * @return 是否有扫描任务创建
     */
    private fun scanOnNodeCreatedEvent(event: ArtifactEvent): Boolean {
        if (!supportFileNameExtension(event.resourceKey)) {
            return false
        }
        if (logger.isDebugEnabled) {
            logger.debug("receive event resourceKey[${event.resourceKey}]")
        }

        var hasScanTask = false
        with(event) {
            scanPlanDao
                .findByProjectIdAndRepoName(projectId, repoName, RepositoryType.GENERIC.name)
                .filter { match(event, it.rule.readJsonString()) }
                .forEach {
                    val request = ScanRequest(
                        planId = it.id!!,
                        rule = RuleConverter.convert(projectId, repoName, resourceKey)
                    )
                    scanService.scan(request, ScanTriggerType.ON_NEW_ARTIFACT, it.lastModifiedBy)
                    hasScanTask = true
                }
        }

        if (!hasScanTask) {
            scanIfHasProjectConfiguration(event)
        }

        return hasScanTask
    }

    private fun supportFileNameExtension(fullPath: String): Boolean {
        val fileNameExtension = fullPath.substringAfterLast('.', "")
        return fileNameExtension in scannerService.supportFileNameExt()
    }

    /**
     * 当package有新版本时执行扫描
     *
     * @param event package新版本创建事件
     *
     * @return 是否有扫描任务创建
     */
    private fun scanOnVersionCreated(event: ArtifactEvent): Boolean {
        var hasScanTask = false

        with(event) {
            val packageType = event.data[VersionCreatedEvent::packageType.name] as String? ?: return false
            logger.info("receive event resourceKey[${event.resourceKey}]")

            scanPlanDao
                .findByProjectIdAndRepoName(projectId, repoName, packageType)
                .filter { match(event, it.rule.readJsonString()) }
                .forEach {
                    val packageKey = data[VersionCreatedEvent::packageKey.name] as String
                    val packageVersion = data[VersionCreatedEvent::packageVersion.name] as String
                    val request = ScanRequest(
                        planId = it.id!!,
                        rule = RuleConverter.convert(projectId, repoName, packageKey, packageVersion)
                    )
                    scanService.scan(request, ScanTriggerType.ON_NEW_ARTIFACT, it.lastModifiedBy)
                    hasScanTask = true
                }
        }

        if (!hasScanTask) {
            scanIfHasProjectConfiguration(event)
        }

        return hasScanTask
    }

    /**
     * 执行系统层面设置的自动扫描
     */
    private fun scanIfHasProjectConfiguration(event: ArtifactEvent) {
        with(event) {
            val autoScanConfiguration = projectScanConfigurationService
                .findProjectOrGlobalScanConfiguration(event.projectId)
                ?.autoScanConfiguration
                ?: return
            for (entry in autoScanConfiguration.entries) {
                val scanner = entry.key
                val configuration = entry.value

                if (!couldApplied(configuration, scanner, event)) {
                    continue
                }

                val rule = if (event.type == EventType.NODE_CREATED) {
                    RuleConverter.convert(projectId, repoName, resourceKey)
                } else {
                    val packageKey = data[VersionCreatedEvent::packageKey.name] as String
                    val packageVersion = data[VersionCreatedEvent::packageVersion.name] as String
                    RuleConverter.convert(projectId, repoName, packageKey, packageVersion)
                }
                val request = ScanRequest(
                    scanner = scanner,
                    rule = rule,
                    metadata = listOf(TaskMetadata(TaskMetadata.TASK_METADATA_GLOBAL, "true"))
                )
                scanService.scan(request, ScanTriggerType.ON_NEW_ARTIFACT_SYSTEM, SYSTEM_USER)
            }
        }
    }

    /**
     * 判断是否能应用配置
     */
    private fun couldApplied(configuration: AutoScanConfiguration, scannerName: String, event: ArtifactEvent): Boolean {
        return (configuration.autoScanRepoNames.isEmpty() || event.repoName in configuration.autoScanRepoNames)
            && match(event, configuration.autoScanMatchRule?.readJsonString<Rule>())
            && supportedEvent(scannerService.get(scannerName), event)
    }

    /**
     * 判断制品是否匹配规则
     *
     * @return true 匹配规则或者rule为null， false 不匹配
     */
    private fun match(event: ArtifactEvent, rule: Rule?): Boolean {
        if (rule == null) {
            return true
        }

        with(event) {
            if (event.type == EventType.NODE_CREATED) {
                val valuesToMatch = mapOf(
                    NodeDetail::projectId.name to projectId,
                    NodeDetail::repoName.name to repoName,
                    RuleArtifact::name.name to resourceKey.substringAfterLast(CharPool.SLASH)
                )
                return RuleMatcher.match(rule, valuesToMatch)
            }

            if ((event.type == EventType.VERSION_CREATED || event.type == EventType.VERSION_UPDATED)) {
                val valuesToMatch = mapOf<String, Any>(
                    PackageSummary::projectId.name to projectId,
                    PackageSummary::repoName.name to repoName,
                    PackageSummary::type.name to data[VersionCreatedEvent::packageType.name] as String,
                    RuleArtifact::name.name to data[VersionCreatedEvent::packageName.name] as String,
                    RuleArtifact::version.name to data[VersionCreatedEvent::packageVersion.name] as String,
                    // 默认当前正在创建的是最新版本
                    RULE_FIELD_LATEST_VERSION to true
                )
                return RuleMatcher.match(rule, valuesToMatch)
            }
        }

        return false
    }

    /**
     * 判断是否为扫描器支持处理的事件
     */
    private fun supportedEvent(scanner: Scanner, event: ArtifactEvent): Boolean {
        val supportPkgTypes = scanner.supportPackageTypes
        return when (event.type) {
            EventType.NODE_CREATED -> RepositoryType.GENERIC.name in supportPkgTypes
            EventType.VERSION_CREATED -> event.data[VersionCreatedEvent::packageType.name] in supportPkgTypes
            else -> false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScanEventConsumer::class.java)
    }
}
