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

package com.tencent.bkrepo.ddc.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

@Component
class DdcMeterBinder(private val registry: MeterRegistry) : MeterBinder {
    /**
     * ref inline加载耗时
     */
    lateinit var refInlineLoadTimer: Timer

    /**
     * ref compact binary 加载耗时
     */
    lateinit var refLoadTimer: Timer

    /**
     * blob 加载耗时
     */
    lateinit var blobLoadTimer: Timer

    /**
     * ref 创建耗时
     */
    lateinit var refStoreTimer: Timer

    override fun bindTo(registry: MeterRegistry) {
        refInlineLoadTimer = Timer
            .builder(DDC_REF)
            .tag("type", "inline")
            .tag("method", "load")
            .register(registry)

        refLoadTimer = Timer
            .builder(DDC_REF)
            .tag("type", "cb")
            .tag("method", "load")
            .register(registry)

        blobLoadTimer = Timer
            .builder(DDC_BLOB)
            .tag("type", "compressed")
            .tag("method", "load")
            .register(registry)

        refStoreTimer = Timer
            .builder(DDC_REF)
            .tag("method", "store")
            .register(registry)
    }

    /**
     * 获取Ref请求总数
     */
    fun incCacheCount(projectId: String, repoName: String) {
        Counter
            .builder(DDC_REF_GETS)
            .tag("projectId", projectId)
            .tag("repoName", repoName)
            .tag("type", "total")
            .register(registry)
            .increment()
    }

    /**
     * Ref命中数
     */
    fun incCacheHitCount(projectId: String, repoName: String) {
        Counter
            .builder(DDC_REF_GETS)
            .tag("projectId", projectId)
            .tag("repoName", repoName)
            .tag("type", "hit")
            .register(registry)
            .increment()

    }

    companion object {
        private const val DDC_REF_GETS = "ddc.ref.gets"
        private const val DDC_REF = "ddc.ref"
        private const val DDC_BLOB = "ddc.blob"
    }
}