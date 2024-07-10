/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.cache.preload.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.concurrent.TimeUnit

@ConfigurationProperties("spring.ai.vectorstore.milvus.client")
data class MilvusServiceClientProperties(
    var host: String = "localhost",
    var port: Int = 19530,
    var uri: String? = null,
    val token: String? = null,
    val connectTimeoutMs: Long = 10000L,
    val keepAliveTimeMs: Long = 55000L,
    val keepAliveTimeoutMs: Long = 20000L,
    val rpcDeadlineMs: Long = 0L,
    val clientKeyPath: String? = null,
    val clientPemPath: String? = null,
    val caPemPath: String? = null,
    val serverPemPath: String? = null,
    val serverName: String? = null,
    val secure: Boolean = false,
    val idleTimeoutMs: Long = TimeUnit.MILLISECONDS.convert(24L, TimeUnit.HOURS),
    val username: String = "root",
    val password: String = "milvus"
)
