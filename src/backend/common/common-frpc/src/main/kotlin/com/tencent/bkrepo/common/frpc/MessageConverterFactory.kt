/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.frpc

import com.tencent.bkrepo.common.frpc.event.AckEvent
import com.tencent.bkrepo.common.frpc.event.EventType
import com.tencent.bkrepo.common.frpc.event.GcPrepareAckEvent
import com.tencent.bkrepo.common.frpc.event.GcPrepareEvent
import com.tencent.bkrepo.common.frpc.event.GcRecoverEvent
import com.tencent.bkrepo.common.frpc.event.LeaderEvent
import com.tencent.bkrepo.common.frpc.event.RegEvent
import com.tencent.bkrepo.common.frpc.event.RequestVoteEvent
import com.tencent.bkrepo.common.frpc.event.VoteEvent

object MessageConverterFactory {
    fun createSupportAllEventMessageConverter(): EventMessageConverter<String> {
        return TextEventMessageConverter().apply {
            registerEvent(EventType.ACK.name, AckEvent::class.java)
            registerEvent(EventType.REG.name, RegEvent::class.java)
            registerEvent(EventType.REQUEST_VOTE.name, RequestVoteEvent::class.java)
            registerEvent(EventType.VOTE.name, VoteEvent::class.java)
            registerEvent(EventType.LEADER.name, LeaderEvent::class.java)
            registerEvent(EventType.GC_PREPARE.name, GcPrepareEvent::class.java)
            registerEvent(EventType.GC_PREPARE_ACK.name, GcPrepareAckEvent::class.java)
            registerEvent(EventType.GC_RECOVER.name, GcRecoverEvent::class.java)
        }
    }
}
