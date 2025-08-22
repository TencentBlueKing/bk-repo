/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.pojo.remote

import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import okhttp3.Headers
import okhttp3.RequestBody
import org.springframework.web.bind.annotation.RequestMethod

/**
 * 请求熟悉，包含请求url，请求头，请求体等内容
 */
data class RequestProperty(
    // 请求url
    var requestUrl: String? = null,
    // Authorization code
    var authorizationCode: String? = null,
    // 请求参数
    var params: String? = null,
    // 请求头
    var headers: Headers? = null,
    // 请求体
    var requestBody: RequestBody? = null,
    // 请求方法
    var requestMethod: RequestMethod? = null,
    // 用户名
    var userName: String? = null,
    // 申请授权的范围
    var scope: String? = null,
    // 请求标签
    var requestTag: RequestTag? = null
)
