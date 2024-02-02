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

import com.tencent.bkrepo.common.artifact.cache.EldestRemovedListener
import com.tencent.bkrepo.common.artifact.cache.OrderedCache

class LocalSLRUCache(
    capacity: Int = 0,
    private val listeners: MutableList<EldestRemovedListener<String, Any?>> = ArrayList()
) : OrderedCache<String, Any?> {

    private val probation = LocalLRUCache(
        (capacity * FACTOR_PROBATION).toInt(),
        mutableListOf(ProbationLRUEldestRemovedListener(listeners))
    )

    private val protected = LocalLRUCache(
        (capacity * FACTOR_PROTECTED).toInt(),
        mutableListOf(ProtectedLRUEldestRemovedListener(probation))
    )

    override fun put(key: String, value: Any?): Any? {
        return when {
            protected.containsKey(key) -> {
                protected.put(key, value)
            }

            probation.containsKey(key) -> {
                // 晋级到protected lru
                val oldVal = probation.remove(key)
                protected[key] = value
                oldVal
            }

            else -> {
                probation[key] = value
                null
            }
        }
    }

    override fun get(key: String): Any? {
        if (protected.containsKey(key)) {
            return protected[key]
        }

        if (probation.containsKey(key)) {
            // 晋级到protected lru
            val oldVal = probation.remove(key)
            protected[key] = oldVal
            return oldVal
        }

        return null
    }

    override fun containsKey(key: String): Boolean {
        return protected.containsKey(key) || probation.containsKey(key)
    }

    override fun remove(key: String): Any? {
        return when {
            protected.containsKey(key) -> protected.remove(key)
            probation.containsKey(key) -> probation.remove(key)
            else -> null
        }
    }

    override fun count(): Int {
        return protected.size + probation.size
    }

    override fun last(): String? {
        return probation.last() ?: protected.last()
    }

    override fun addEldestRemovedListener(listener: EldestRemovedListener<String, Any?>) {
        listeners.add(listener)
    }

    override fun getEldestRemovedListeners() = listeners

    override fun weight(): Long = protected.weight() + probation.weight()

    override fun setMaxWeight(max: Long) {
        protected.setMaxWeight((max * FACTOR_PROTECTED).toLong())
        probation.setMaxWeight((max * FACTOR_PROBATION).toLong())
    }

    override fun getMaxWeight() = protected.getMaxWeight() + probation.getMaxWeight()

    override fun setCapacity(capacity: Int) {
        protected.setCapacity((capacity * FACTOR_PROTECTED).toInt())
        probation.setCapacity((capacity * FACTOR_PROBATION).toInt())
    }

    override fun getCapacity() = protected.getCapacity() + probation.getCapacity()

    override fun setKeyWeightSupplier(supplier: (k: String, v: Any?) -> Long) {
        protected.setKeyWeightSupplier(supplier)
        probation.setKeyWeightSupplier(supplier)
    }

    fun probationFull() = probation.getCapacity() > 0L && probation.size >= probation.getCapacity()
            || probation.getMaxWeight() > 0L && probation.weight() >= probation.getMaxWeight()

    private class ProtectedLRUEldestRemovedListener(
        private val probationLru: LocalLRUCache
    ) : EldestRemovedListener<String, Any?> {
        override fun onEldestRemoved(key: String, value: Any?) {
            probationLru[key] = value
        }
    }

    private class ProbationLRUEldestRemovedListener(
        private val parentListeners: List<EldestRemovedListener<String, Any?>>,
    ) : EldestRemovedListener<String, Any?> {
        override fun onEldestRemoved(key: String, value: Any?) {
            parentListeners.forEach { it.onEldestRemoved(key, value) }
        }
    }

    companion object {
        private const val FACTOR_PROBATION = 0.2
        private const val FACTOR_PROTECTED = 0.8
    }
}
