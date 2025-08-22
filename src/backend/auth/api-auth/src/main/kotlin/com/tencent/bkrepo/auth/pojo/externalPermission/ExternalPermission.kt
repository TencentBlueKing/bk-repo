/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.pojo.externalPermission

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "外部权限")
data class ExternalPermission(
    @get:Schema(title = "id")
    val id: String,
    @get:Schema(title = "外部权限回调地址")
    val url: String,
    @get:Schema(title = "请求头")
    val headers: Map<String, String>? = emptyMap(),
    @get:Schema(title = "项目id")
    val projectId: String,
    @get:Schema(title = "仓库名")
    val repoName: String,
    @get:Schema(title = "生效微服务")
    val scope: String,
    @get:Schema(title = "平台账号白名单，白名单内不会校验外部权限")
    val platformWhiteList: List<String>? = emptyList(),
    @get:Schema(title = "是否启用")
    val enabled: Boolean,
    @get:Schema(title = "创建日期")
    var createdDate: LocalDateTime,
    @get:Schema(title = "创建人")
    var createdBy: String,
    @get:Schema(title = "最后修改日期")
    var lastModifiedDate: LocalDateTime,
    @get:Schema(title = "最后修改人")
    var lastModifiedBy: String
)
