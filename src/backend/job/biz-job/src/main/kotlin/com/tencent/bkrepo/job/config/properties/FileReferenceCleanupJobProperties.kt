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

package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.file-reference-cleanup")
class FileReferenceCleanupJobProperties(
    override var cron: String = "0 0 4/6 * * ?",
    override var sharding: Boolean = true,
    /**
     * 预期系统节点数
     * */
    var expectedNodes: Long = 100_000_000, // 1e
    /**
     * 布隆过滤器的误报率。
     * 误报率较高，会导致更多的数据库查询，但不影响节点清理的正确性，误报率越低，消耗的内存越大。
     * */
    var fpp: Double = 0.0001,
    /**
     * 是否试运行，true则表示不会进行任务数据的删除，false会进行实际删除
     * */
    var dryRun: Boolean = false,
    /**
     * 忽略的存储凭据，这些存储的缓存将不执行清理
     */
    var ignoredStorageCredentialsKeys: Set<String> = emptySet(),
) : MongodbJobProperties()
