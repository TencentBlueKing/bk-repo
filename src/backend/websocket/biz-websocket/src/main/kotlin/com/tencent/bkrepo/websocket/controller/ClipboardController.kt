/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.websocket.controller

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.websocket.constant.SESSION_ID
import com.tencent.bkrepo.websocket.pojo.fs.CopyPDU
import com.tencent.bkrepo.websocket.pojo.fs.PastePDU
import com.tencent.bkrepo.websocket.pojo.fs.PingPongPDU
import com.tencent.bkrepo.websocket.service.ClipboardService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
@MessageMapping("/clipboard")
class ClipboardController(
    private val clipboardService: ClipboardService
) {

    @MessageMapping("/ping")
    fun ping(pingPDU: PingPongPDU, accessor: SimpMessageHeaderAccessor) {
        val userId = accessor.sessionAttributes?.get(USER_KEY)?.toString() ?: ANONYMOUS_USER
        pingPDU.sessionId = accessor.sessionAttributes?.get(SESSION_ID)?.toString()
        clipboardService.ping(userId, pingPDU)
    }

    @MessageMapping("/pong")
    fun pong(pongPDU: PingPongPDU, accessor: SimpMessageHeaderAccessor) {
        val userId = accessor.sessionAttributes?.get(USER_KEY)?.toString() ?: ANONYMOUS_USER
        clipboardService.pong(userId, pongPDU)
    }

    @MessageMapping("/copy")
    fun copy(copyPDU: CopyPDU, accessor: SimpMessageHeaderAccessor) {
        val userId = accessor.sessionAttributes?.get(USER_KEY)?.toString() ?: ANONYMOUS_USER
        copyPDU.sessionId = accessor.sessionAttributes?.get(SESSION_ID)?.toString()
        clipboardService.copy(userId, copyPDU)
    }

    @MessageMapping("/paste")
    fun paste(pastePDU: PastePDU) {
        clipboardService.paste(pastePDU)
    }
}

