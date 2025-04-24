/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.webhook.pojo

import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.webhook.constant.WebHookRequestStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "WebHook日志信息")
data class WebHookLog(
    @get:Schema(title = "id")
    val id: String,
    @get:Schema(title = "WebHook回调地址")
    val webHookUrl: String,
    @get:Schema(title = "触发事件")
    val triggeredEvent: EventType,
    @get:Schema(title = "请求头")
    val requestHeaders: Map<String, String>,
    @get:Schema(title = "请求载荷")
    val requestPayload: String,
    @get:Schema(title = "请求状态")
    val status: WebHookRequestStatus,
    @get:Schema(title = "响应头")
    val responseHeaders: Map<String, String>? = null,
    @get:Schema(title = "响应体")
    val responseBody: String? = null,
    @get:Schema(title = "请求耗时")
    val requestDuration: Long,
    @get:Schema(title = "请求时间")
    val requestTime: LocalDateTime,
    @get:Schema(title = "错误信息")
    val errorMsg: String? = null
)
