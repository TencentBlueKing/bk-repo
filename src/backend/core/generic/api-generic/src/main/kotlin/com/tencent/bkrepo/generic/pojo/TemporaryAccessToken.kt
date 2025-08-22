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

package com.tencent.bkrepo.generic.pojo

import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "临时访问token")
data class TemporaryAccessToken(
    @get:Schema(title = "项目")
    val projectId: String,
    @get:Schema(title = "仓库")
    val repoName: String,
    @get:Schema(title = "授权路径")
    val fullPath: String,
    @get:Schema(title = "token")
    val token: String,
    @get:Schema(title = "授权用户")
    val authorizedUserList: Set<String>,
    @get:Schema(title = "授权IP")
    val authorizedIpList: Set<String>,
    @get:Schema(title = "过期时间")
    val expireDate: String?,
    @get:Schema(title = "允许下载次数")
    var permits: Int?,
    @get:Schema(title = "token类型")
    val type: String
)
