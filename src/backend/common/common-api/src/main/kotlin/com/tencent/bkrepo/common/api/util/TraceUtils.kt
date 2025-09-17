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

package com.tencent.bkrepo.common.api.util

import io.micrometer.common.KeyValues
import io.micrometer.context.ContextExecutorService
import io.micrometer.context.ContextSnapshotFactory
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

object TraceUtils {

    private val contextSnapshotFactory = ContextSnapshotFactory.builder().build()

    fun Runnable.trace(): Runnable {
        return try {
            contextSnapshotFactory.captureAll().wrap(this)
        } catch (_: Exception) {
            this
        }
    }

    fun <T> Callable<T>.trace(): Callable<T> {
        return try {
            contextSnapshotFactory.captureAll().wrap(this)
        } catch (_: Exception) {
            this
        }
    }

    fun ExecutorService.trace(): ExecutorService {
        return ContextExecutorService.wrap(this, contextSnapshotFactory::captureAll)
    }

    fun <T> newSpan(
        observationRegistry: ObservationRegistry,
        spanName: String,
        lowCardinalityKeyValues: KeyValues,
        highCardinalityKeyValues: KeyValues,
        action: () -> T
    ): T {
        // webflux中kotlin协程调用时, context是空的, 此时用空context创建新span会导致traceId丢失
        val contextName = observationRegistry.currentObservation?.context?.name
        if (contextName.isNullOrEmpty() || contextName == "null") {
            return action()
        }
        return Observation.createNotStarted(spanName, observationRegistry)
            .lowCardinalityKeyValues(lowCardinalityKeyValues)
            .highCardinalityKeyValues(highCardinalityKeyValues)
            .observe(action)!!
    }
}
