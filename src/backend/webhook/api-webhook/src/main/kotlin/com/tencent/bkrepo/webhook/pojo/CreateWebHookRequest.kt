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
import com.tencent.bkrepo.common.metadata.annotation.Sensitive
import com.tencent.bkrepo.common.metadata.handler.MaskPartMapValue
import com.tencent.bkrepo.webhook.constant.AssociationType
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "创建WebHook请求")
data class CreateWebHookRequest(
    @get:Schema(title = "回调地址")
    val url: String,
    @get:Schema(title = "自定义请求头")
    @Sensitive(handler = MaskPartMapValue::class)
    val headers: Map<String, String> = emptyMap(),
    @get:Schema(title = "触发事件")
    val triggers: List<EventType>,
    @get:Schema(title = "关联对象类型")
    val associationType: AssociationType,
    @get:Schema(title = "关联对象id")
    val associationId: String = "",
    @get:Schema(title = "事件资源key正则模式")
    val resourceKeyPattern: String? = null
)
