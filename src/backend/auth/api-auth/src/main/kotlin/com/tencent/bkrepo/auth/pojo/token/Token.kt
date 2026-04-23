/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.auth.pojo.token

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Transient
import java.time.LocalDateTime

@Schema(title = "token信息")
data class Token(
    @get:Schema(title = "tokenName")
    val name: String?,
    @get:Schema(title = "tokenID")
    val id: String,
    @get:Schema(title = "创建时间")
    val createdAt: LocalDateTime,
    @get:Schema(title = "过期时间")
    val expiredAt: LocalDateTime?,

    /**
     * 可直接使用的认证凭证串。
     *
     * - **仅在"创建 token"类接口的响应中填充**，用于让客户端免去自行拼接 `Basic base64(userId:token)` 的步骤；
     * - 标记为 [Transient]，MongoDB 不会把该字段序列化到 `user.tokens` 集合中，避免明文凭证落库；
     * - 其他返回路径（列表/查询等）应保持为 `null`，防止明文凭证通过查询接口泄漏。
     */
    @get:Schema(title = "可直接使用的认证凭证，仅创建 token 时返回")
    @field:Transient
    val authorization: Authorization? = null
)
