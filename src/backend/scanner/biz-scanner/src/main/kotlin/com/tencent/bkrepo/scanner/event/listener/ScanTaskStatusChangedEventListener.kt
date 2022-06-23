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

package com.tencent.bkrepo.scanner.event.listener

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.NotifyService
import com.tencent.bkrepo.common.notify.api.message.weworkbot.MarkdownMessage
import com.tencent.bkrepo.common.notify.api.message.weworkbot.WeworkBot
import com.tencent.bkrepo.common.scanner.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils.getLocalizedMessage
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.scanner.configuration.ScannerProperties
import com.tencent.bkrepo.scanner.event.ScanTaskStatusChangedEvent
import com.tencent.bkrepo.scanner.extension.ScanResultNotifyContext
import com.tencent.bkrepo.scanner.extension.ScanResultNotifyExtension
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_CVE_CRITICAL
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_CVE_HIGH
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_CVE_LOW
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_CVE_MEDIUM
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_DETAIL
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_FAILED
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_SCANNED
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_TITLE
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_TRIGGER_TIME
import com.tencent.bkrepo.scanner.message.ScannerMessageCode.SCAN_REPORT_NOTIFY_MESSAGE_TRIGGER_USER
import com.tencent.bkrepo.scanner.pojo.ScanPlan
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import com.tencent.bkrepo.scanner.pojo.TaskMetadata
import com.tencent.devops.plugin.api.PluginManager
import com.tencent.devops.plugin.api.applyExtension
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
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
        if (event.task.status == ScanTaskStatus.FINISHED.name) {
            notify(event.task)
        }
    }

    fun setWeworkBotUrl(scanTaskId: String, url: String, chatIds: String? = null) {
        val key = weworkBotKey(scanTaskId)
        val bot = WeworkBot(webhookUrl = url, chatIds = chatIds)
        redisTemplate.opsForValue().set(key, bot.toJsonString(), DEFAULT_EXPIRED_DAY, TimeUnit.DAYS)
    }

    private fun notify(scanTask: ScanTask) {
        if (scanTask.projectId == null || scanTask.scanPlan == null) {
            return
        }

        val messageBody = buildMarkdownMessage(scanTask)

        applyNotifyPlugin(scanTask, messageBody)
        weworkBotNotify(scanTask, messageBody)
    }

    private fun applyNotifyPlugin(scanTask: ScanTask, message: String) {
        // 不通知匿名用户和系统用户
        if (scanTask.createdBy == ANONYMOUS_USER || scanTask.createdBy == SYSTEM_USER) {
            return
        }

        val reportUrl = reportUrl(scanTask.projectId!!, scanTask.taskId, scanTask.scanPlan!!)
        val context = ScanResultNotifyContext(
            userIds = setOf(scanTask.createdBy),
            reportUrl = reportUrl,
            scanTask = scanTask,
            body = message
        )
        pluginManager.applyExtension<ScanResultNotifyExtension> { notify(context) }
    }

    private fun weworkBotNotify(scanTask: ScanTask, message: String) {
        val weworkBot = getWeworkBot(scanTask.taskId) ?: return
        send(weworkBot, message)
    }

    private fun buildMarkdownMessage(task: ScanTask): String {
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
            getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_SCANNED, arrayOf(task.scanned))
        )
        summary.append(
            getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_FAILED, arrayOf(task.failed))
        )

        // 扫描结果预览
        cveMessageCodeMap.forEach {
            val count = task.scanResultOverview?.get(it.key.key) ?: 0L
            summary.append(getLocalizedMessage(it.value, arrayOf(count)))
        }

        //详细报告地址
        val reportUrl = reportUrl(task.projectId!!, task.taskId, task.scanPlan!!)
        summary.append(getLocalizedMessage(SCAN_REPORT_NOTIFY_MESSAGE_DETAIL, arrayOf(reportUrl)))

        return summary.toString()
    }

    @Suppress("MaxLineLength")
    private fun reportUrl(projectId: String, taskId: String, scanPlan: ScanPlan) =
        "${scannerProperties.detailReportUrl}/ui/${projectId}/preview/scanTask/${scanPlan.id!!}/${taskId}?scanType=${scanPlan.type}"

    private fun send(bot: WeworkBot, message: String) {
        notifyService.sendWeworkBot(bot, MarkdownMessage(message))
    }

    private fun getWeworkBot(scanTaskId: String): WeworkBot? {
        return redisTemplate.opsForValue().get(weworkBotKey(scanTaskId))?.readJsonString()
    }

    private fun weworkBotKey(scanTaskId: String) = "scanner:taskId:${scanTaskId}:wework:bot"

    companion object {
        private val cveMessageCodeMap = mapOf(
            CveOverviewKey.CVE_LOW_COUNT to SCAN_REPORT_NOTIFY_MESSAGE_CVE_LOW,
            CveOverviewKey.CVE_MEDIUM_COUNT to SCAN_REPORT_NOTIFY_MESSAGE_CVE_MEDIUM,
            CveOverviewKey.CVE_HIGH_COUNT to SCAN_REPORT_NOTIFY_MESSAGE_CVE_HIGH,
            CveOverviewKey.CVE_CRITICAL_COUNT to SCAN_REPORT_NOTIFY_MESSAGE_CVE_CRITICAL
        )
        private const val DEFAULT_EXPIRED_DAY = 1L
    }
}
