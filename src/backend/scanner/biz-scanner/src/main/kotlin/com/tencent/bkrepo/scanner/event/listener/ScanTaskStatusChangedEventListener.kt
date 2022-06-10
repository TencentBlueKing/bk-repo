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

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.NotifyService
import com.tencent.bkrepo.common.notify.api.message.weworkbot.MarkdownMessage
import com.tencent.bkrepo.common.notify.api.message.weworkbot.WeworkBot
import com.tencent.bkrepo.common.scanner.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.scanner.configuration.ScannerProperties
import com.tencent.bkrepo.scanner.event.ScanTaskStatusChangedEvent
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit


@Component
class ScanTaskStatusChangedEventListener(
    private val redisTemplate: RedisTemplate<String, String>,
    private val notifyService: NotifyService,
    private val scannerProperties: ScannerProperties
) {
    @Async
    @EventListener(ScanTaskStatusChangedEvent::class)
    fun listen(event: ScanTaskStatusChangedEvent) {
        with(event.task) {
            if (status == ScanTaskStatus.FINISHED.name) {
                val message = buildMarkdownMessage(event.task).ifBlank { return }
                val weworkBot = getWeworkBot(taskId) ?: return
                send(weworkBot, message)
            }
        }
    }

    fun setWeworkBotUrl(scanTaskId: String, url: String, chatIds: String? = null) {
        val key = weworkBotKey(scanTaskId)
        val bot = WeworkBot(webhookUrl = url, chatIds = chatIds)
        redisTemplate.opsForValue().set(key, bot.toJsonString(), DEFAULT_EXPIRED_DAY, TimeUnit.DAYS)
    }

    private fun buildMarkdownMessage(task: ScanTask): String {
        if (task.projectId == null || task.scanPlan == null) {
            return ""
        }
        val projectId = task.projectId
        val planId = task.scanPlan!!.id!!

        val summary = StringBuilder()

        summary.append("**${task.total}** artifact scanned.")
        if (task.failed != 0L) {
            summary.append("**${task.failed}** failed.")
        }

        CveOverviewKey.values().forEach { key ->
            val count = task.scanResultOverview?.get(key.key) ?: 0L
            summary.append("\n${key.level.levelName}: **$count**")
        }

        summary.append("\n[detail](${scannerProperties.detailReportUrl}/ui/${projectId}/scanReport/${planId})")

        return summary.toString()
    }

    private fun send(bot: WeworkBot, message: String) {
        notifyService.sendWeworkBot(bot, MarkdownMessage(message))
    }

    private fun getWeworkBot(scanTaskId: String): WeworkBot? {
        return redisTemplate.opsForValue().get(weworkBotKey(scanTaskId))?.readJsonString()
    }

    private fun weworkBotKey(scanTaskId: String) = "scanner:taskId:${scanTaskId}:wework:bot"

    companion object {
        private const val DEFAULT_EXPIRED_DAY = 1L
    }
}
