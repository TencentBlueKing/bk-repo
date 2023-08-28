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

package com.tencent.bkrepo.analyst.event.listener

import com.tencent.bkrepo.analyst.component.manager.ScannerConverter.Companion.OVERVIEW_KEY_SENSITIVE_TOTAL
import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.event.ScanTaskStatusChangedEvent
import com.tencent.bkrepo.analyst.extension.ScanResultNotifyContext
import com.tencent.bkrepo.analyst.extension.ScanResultNotifyExtension
import com.tencent.bkrepo.analyst.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_CVE
import com.tencent.bkrepo.analyst.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_LICENSE
import com.tencent.bkrepo.analyst.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_DETAIL
import com.tencent.bkrepo.analyst.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_SCANNED
import com.tencent.bkrepo.analyst.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_SENSITIVE
import com.tencent.bkrepo.analyst.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_TITLE
import com.tencent.bkrepo.analyst.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_TRIGGER_TIME
import com.tencent.bkrepo.analyst.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_TRIGGER_USER
import com.tencent.bkrepo.analyst.pojo.ScanPlan
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.common.analysis.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseOverviewKey.overviewKeyOf
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanType
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.message.MessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.NotifyService
import com.tencent.bkrepo.common.notify.api.weworkbot.TextMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotChannelCredential
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotMessage
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.devops.plugin.api.PluginManager
import com.tencent.devops.plugin.api.applyExtension
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.Locale
import java.util.concurrent.TimeUnit


@Component
class ScanTaskStatusChangedEventListener(
    private val pluginManager: PluginManager,
    private val redisTemplate: RedisTemplate<String, String>,
    private val notifyService: NotifyService,
    private val scannerProperties: ScannerProperties
) {
    @Async
    @EventListener(ScanTaskStatusChangedEvent::class)
    fun listen(event: ScanTaskStatusChangedEvent) {
        if (event.task.status == ScanTaskStatus.FINISHED.name && event.task.scanned != 0L) {
            notify(event.task)
        }
    }

    fun setWeworkBotUrl(scanTaskId: String, url: String? = null, chatIds: String? = null) {
        val key = weworkBotKey(scanTaskId)
        val bot = WeworkBot(webhookUrl = url, chatIds = chatIds)
        redisTemplate.opsForValue().set(key, bot.toJsonString(), DEFAULT_EXPIRED_DAY, TimeUnit.DAYS)
    }

    private fun notify(scanTask: ScanTask) {
        if (scanTask.projectId == null || scanTask.scanPlan == null) {
            return
        }

        // 不通知匿名用户和系统用户
        if (scanTask.createdBy == ANONYMOUS_USER || scanTask.createdBy == SYSTEM_USER) {
            return
        }

        // 由于制品库存在独立用户，用户id不统一，暂时只对流水线触发的扫描进行通知
        if (scanTask.triggerType != ScanTriggerType.PIPELINE.name) {
            return
        }

        val message = buildMessage(scanTask)
        weworkBotNotify(scanTask, message)
        applyNotifyPlugin(scanTask, message)
    }

    private fun applyNotifyPlugin(scanTask: ScanTask, message: String) {
        pluginManager.applyExtension<ScanResultNotifyExtension> {
            val reportUrl = reportUrl(scanTask.projectId!!, scanTask.taskId, scanTask.scanPlan!!)
            val context = ScanResultNotifyContext(
                userIds = setOf(scanTask.createdBy),
                reportUrl = reportUrl,
                scanTask = scanTask,
                body = message
            )
            notify(context)
        }
    }

    private fun weworkBotNotify(scanTask: ScanTask, message: String) {
        val weworkBot = getWeworkBot(scanTask.taskId)
        val botWebhookUrl = weworkBot?.webhookUrl
        var chatIds = weworkBot?.chatIds?.split("|")?.toSet()
        if (botWebhookUrl != null) {
            val webhookKey = botWebhookUrl.toHttpUrlOrNull()?.queryParameter("key")
            if (webhookKey.isNullOrEmpty()) {
                logger.warn("get webhook key failed[${weworkBot.webhookUrl}]")
                return
            }
            val credential = WeworkBotChannelCredential(key = webhookKey)
            notifyService.send(
                WeworkBotMessage(TextMessage(message), chatIds),
                credential
            )
        } else {
            if (chatIds.isNullOrEmpty()) {
                chatIds = setOf(scanTask.createdBy)
            }
            notifyService.send(WeworkBotMessage(TextMessage(message), chatIds))
        }
        logger.info("notify by wework bot taskId[{${scanTask.taskId}}]")
    }

    private fun buildMessage(task: ScanTask): String {
        val metadata = task.metadata.associateBy { it.key }

        val summary = StringBuilder()
        // 标题
        summary.append(getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_TITLE))
        val pipelineName = metadata[TaskMetadata.TASK_METADATA_PIPELINE_NAME]
        val buildNumber = metadata[TaskMetadata.TASK_METADATA_BUILD_NUMBER]
        if (pipelineName != null && buildNumber != null) {
            summary.append("${pipelineName.value}(#${buildNumber.value})")
        }

        // 触发用户
        summary.append(
            getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_TRIGGER_USER, arrayOf(task.createdBy))
        )

        //触发时间
        val triggerTime = task.triggerDateTime
        summary.append(
            getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_TRIGGER_TIME, arrayOf(triggerTime))
        )

        // 扫描制品数
        summary.append(
            getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_SCANNED, arrayOf(task.scanned, task.failed)),
            "\n"
        )
        // 扫描结果预览
        if (task.scanPlan?.scanTypes?.contains(ScanType.SENSITIVE.name) == true) {
            val sensitiveCount = task.scanResultOverview?.get(OVERVIEW_KEY_SENSITIVE_TOTAL) ?: 0
            summary.append(getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_SENSITIVE, arrayOf(sensitiveCount)))
        }
        if (task.scanPlan?.scanTypes?.contains(ScanType.SECURITY.name) == true) {
            val params = cveOverviewKey.map { task.scanResultOverview?.get(it) ?: 0 }.toTypedArray()
            summary.append(getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_CVE, params))
        }
        if (task.scanPlan?.scanTypes?.contains(ScanType.LICENSE.name) == true) {
            val params = licenseOverviewKey.map { task.scanResultOverview?.get(it) ?: 0 }.toTypedArray()
            summary.append(getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_LICENSE, params))
        }

        //详细报告地址
        val reportUrl = reportUrl(task.projectId!!, task.taskId, task.scanPlan!!)
        summary.append("\n", getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_DETAIL, arrayOf(reportUrl)))

        return summary.toString()
    }

    private fun getLocalizedMessage(
        messageCode: MessageCode,
        params: Array<out Any>? = null,
        locale: Locale = Locale.SIMPLIFIED_CHINESE
    ): String {
        return LocaleMessageUtils.getLocalizedMessage(messageCode, params, locale)
    }

    @Suppress("MaxLineLength")
    private fun reportUrl(projectId: String, taskId: String, scanPlan: ScanPlan): String {
        val baseUrl = if (projectId.startsWith(GIT_PROJECT_PREFIX)) {
            // 蓝盾Stream的项目鉴权方式不同，需要直接访问制品库前端查看报告
            scannerProperties.frontEndBaseUrl
        } else {
            scannerProperties.detailReportUrl
        }
        return "$baseUrl/${projectId}/preview/scanTask/${scanPlan.id!!}/${taskId}?scanType=${scanPlan.type}"
    }

    private fun getWeworkBot(scanTaskId: String): WeworkBot? {
        return redisTemplate.opsForValue().get(weworkBotKey(scanTaskId))?.readJsonString()
    }

    private fun weworkBotKey(scanTaskId: String) = "scanner:taskId:${scanTaskId}:wework:bot"

    companion object {
        private val cveOverviewKey = listOf(
            CveOverviewKey.CVE_CRITICAL_COUNT.key,
            CveOverviewKey.CVE_HIGH_COUNT.key,
            CveOverviewKey.CVE_MEDIUM_COUNT.key,
            CveOverviewKey.CVE_LOW_COUNT.key
        )
        private val licenseOverviewKey = listOf(
            overviewKeyOf(LicenseNature.UN_RECOMMEND.natureName),
            overviewKeyOf(LicenseNature.UN_COMPLIANCE.natureName),
            overviewKeyOf(LicenseNature.UNKNOWN.natureName)
        )
        private val logger = LoggerFactory.getLogger(ScanTaskStatusChangedEventListener::class.java)
        private const val DEFAULT_EXPIRED_DAY = 1L
        private const val GIT_PROJECT_PREFIX = "git_"
    }

    /**
     * 企业微信机器人
     */
    data class WeworkBot(
        /**
         * 用于发消息的webhook地址
         */
        val webhookUrl: String?,
        /**
         * 需要发消息的会话id，多个id用|分隔
         */
        val chatIds: String? = null
    )
}
