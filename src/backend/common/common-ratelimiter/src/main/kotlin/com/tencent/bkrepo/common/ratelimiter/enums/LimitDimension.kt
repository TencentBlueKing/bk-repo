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

package com.tencent.bkrepo.common.ratelimiter.enums

/**
 * 限流维度：
 */
enum class LimitDimension {
    URL,   // 针对指定URL限流
    URL_REPO, // 针对访问指定项目/仓库的url进行限流
    UPLOAD_USAGE, // 针对仓库上传总大小进行限流
    DOWNLOAD_USAGE,   // 针对仓库下载总大小进行限流
    USER_URL,  // 针对指定用户指定请求进行限流
    USER_URL_REPO, // 针对指定用户访问指定项目/仓库的url进行限流
    USER_UPLOAD_USAGE,  // 针对指定用户上传总大小进行限流
    USER_DOWNLOAD_USAGE,  // 针对指定用户下载总大小进行限流
    UPLOAD_BANDWIDTH, // 针对项目维度上传带宽进行限流
    DOWNLOAD_BANDWIDTH, // 针对项目维度下载带宽进行限流
}
