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

package com.tencent.bkrepo.common.storage.core.config

import org.springframework.util.unit.DataSize
import java.time.Duration

/**
 * 重定向请求配置
 */
data class RedirectProperties(
    /**
     * 是否启用重定向
     */
    var enabled: Boolean = true,

    /**
     * 最小允许直接从后端存储下载文件的限制，只有文件大小超过该值才允许被重定向从实际存储中下载文件
     */
    var minDirectDownloadSize: DataSize = DataSize.ofMegabytes(1L),

    /**
     * 每秒上传到后端实际存储的速度
     */
    var uploadSizePerSecond: DataSize = DataSize.ofMegabytes(10L),

    /**
     * 是否重定向所有下载请求
     */
    var redirectAllDownload: Boolean = false,

    /**
     * 重定向URL的过期时间
     */
    var redirectUrlExpireTime: Duration = Duration.ofMinutes(3L),

    /**
     * 指定存储在重定向时使用的域名
     * key为存储Key
     * value为域名，例如http://bkrepo.example.com
     */
    var storageHosts: Map<String, String> = emptyMap()
)
