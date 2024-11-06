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

package com.tencent.bkrepo.analysis.executor.dependencycheck

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.checker.pojo.DependencyInfo
import com.tencent.bkrepo.common.checker.util.DependencyCheckerUtils
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.result.DependencyItem
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.result.DependencyScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.scanner.DependencyScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.normalizedLevel
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.analysis.executor.ScanExecutor
import com.tencent.bkrepo.analysis.executor.pojo.ScanExecutorTask
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component("${DependencyScanner.TYPE}Executor")
class DependencyScanExecutor @Autowired constructor(
    private val storageProperties: StorageProperties
) : ScanExecutor {

    override fun scan(task: ScanExecutorTask): ScanExecutorResult {
        logger.info("task:${task.toJsonString()}")
        require(task.scanner is DependencyScanner)
        try {
            val sha256 = task.sha256
            val first = sha256.substring(0, 2)
            val second = sha256.substring(2, 4)
            logger.info("storageProperties:${storageProperties.toJsonString()}")
            val path = storageProperties.filesystem.path
            val storePath = if (!path.startsWith("/")) {
                "${System.getProperties()["user.dir"]}/$path".removeSuffix("/")
            } else {
                path.removeSuffix("/")
            }
            val filePath = "$storePath/$first/$second/$sha256"
            logger.info("scan file path:$filePath")
            // 执行扫描
            val dependencyInfo = DependencyCheckerUtils.scanWithInfo(filePath)
            return result(dependencyInfo, filePath)
        } catch (e: Exception) {
            logger.error(logMsg(task, "scan failed"), e)
            throw e
        }
    }

    override fun stop(taskId: String): Boolean {
        // DependencyCheck停止任务暂不做处理
        return true
    }

    /**
     * 解析扫描结果
     */
    private fun result(dependencyInfo: DependencyInfo, prefix: String): DependencyScanExecutorResult {
        logger.debug("dependencyInfo:${dependencyInfo.toJsonString()}")
        val dependencyItems = mutableListOf<DependencyItem>()
        // 遍历依赖
        dependencyInfo.dependencies.forEach { dependency ->
            // 遍历漏洞
            dependency.vulnerabilities?.forEach { vulnerability ->
                val packages = dependency.packages?.get(0)?.id?.removePrefix("pkg:")?.split("@")
                logger.debug("packages:${packages?.toJsonString()}")
                packages?.let {
                    dependencyItems.add(
                        DependencyItem(
                            cveId = vulnerability.name,
                            name = vulnerability.name,
                            dependency = packages[0],
                            version = packages[1],
                            severity = normalizedLevel(vulnerability.severity),
                            description = vulnerability.description,
                            officialSolution = null,
                            defenseSolution = null,
                            references = vulnerability.references.map { reference -> reference.url },
                            cvssV2Vector = vulnerability.cvssv2,
                            cvssV3 = vulnerability.cvssv3,
                            path = dependency.filePath.removePrefix(prefix)
                        )
                    )
                }
            }
        }
        logger.debug("dependencyItems:${dependencyItems.toJsonString()}")

        return DependencyScanExecutorResult(
            scanStatus = SubScanTaskStatus.SUCCESS.name,
            dependencyItems = dependencyItems
        )
    }

    private fun logMsg(task: ScanExecutorTask, msg: String) = with(task) {
        "$msg, parentTaskId[$parentTaskId], subTaskId[$taskId], sha256[$sha256], scanner[${scanner.name}]]"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DependencyScanExecutor::class.java)
    }
}
