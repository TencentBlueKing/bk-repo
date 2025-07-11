/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.listener

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.springframework.context.annotation.Conditional
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 事件审计记录监听器
 */
@Component
@Conditional(SyncCondition::class)
class EventAuditListener(
    private val operateLogService: OperateLogService
) {

    /**
     * 将需要审计记录的事件持久化
     */
    @EventListener(ArtifactEvent::class)
    fun handle(event: ArtifactEvent) {
        operateLogService.saveEventAsync(event, HttpContextHolder.getClientAddress())
    }

    /**
     * 将需要审计记录的事件持久化
     */
    @EventListener(List::class)
    fun handleMulti(events: List<ArtifactEvent>) {
        operateLogService.saveEventsAsync(
            events,
            events.first().data["realIpAddress"] as? String ?: HttpContextHolder.getClientAddress()
        )
    }
}
