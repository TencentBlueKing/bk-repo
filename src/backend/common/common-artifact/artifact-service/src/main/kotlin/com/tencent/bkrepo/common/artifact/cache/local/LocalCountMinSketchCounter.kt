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

package com.tencent.bkrepo.common.artifact.cache.local

import com.tencent.bkrepo.common.artifact.cache.Counter

class LocalCountMinSketchCounter : Counter {
    private val counts = HashMap<String, Int>()
    private var totalAccessCount = 0
    override fun incAndGet(key: String, n: Int): Int {
        incTotalAccessCount()
        val c = counts.getOrDefault(key, 0)
        counts[key] = c + n
        return counts[key]!!
    }

    override fun inc(key: String): Boolean {
        return incAndGet(key) > 1
    }

    override fun get(key: String): Int {
        incTotalAccessCount()
        return counts.getOrDefault(key, 0)
    }

    override fun reset() {
        counts.forEach { counts[it.key] = it.value / 2 }
    }

    override fun reset(key: String): Int {
        counts[key]?.let { counts[key] = it / 2 }
        return get(key)
    }

    override fun clear() {
        counts.clear()
    }

    override fun compare(k1: String, k2: String): Int {
        return counts.getOrDefault(k1, 0).compareTo(counts.getOrDefault(k2, 0))
    }

    private fun incTotalAccessCount() {
        totalAccessCount++
        if (totalAccessCount > MAX_ACCESS_COUNT) {
            totalAccessCount = 0
            reset()
        }
    }

    companion object {
        private const val MAX_ACCESS_COUNT = 5000
    }
}
