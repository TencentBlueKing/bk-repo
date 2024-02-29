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

package com.tencent.bkrepo.common.storage.core.cache.evication.local

import com.tencent.bkrepo.common.storage.core.cache.evication.EldestRemovedListener
import com.tencent.bkrepo.common.storage.core.cache.evication.StorageCacheIndexer

abstract class SLRUCacheIndexer<K, V>(
    private val listeners: MutableList<EldestRemovedListener<K, V>> = ArrayList()
) : StorageCacheIndexer<K, V> {

    protected abstract val probation: StorageCacheIndexer<K, V>

    protected abstract val protected: StorageCacheIndexer<K, V>

    override fun put(key: K, value: V, score: Double?): V? {
        return when {
            protected.containsKey(key) -> {
                protected.put(key, value)
            }

            probation.containsKey(key) -> {
                // 晋级到protected lru
                val oldVal = probation.remove(key)
                protected.put(key, value)
                oldVal
            }

            else -> {
                if (probation.full() && !protected.full()) {
                    protected.put(key, value)
                } else {
                    probation.put(key, value)
                }
                null
            }
        }
    }

    override fun get(key: K): V? {
        if (protected.containsKey(key)) {
            return protected.get(key)
        }

        if (probation.containsKey(key)) {
            // 晋级到protected lru
            val oldVal = probation.remove(key)!!
            protected.put(key, oldVal)
            return oldVal
        }

        return null
    }

    override fun containsKey(key: K): Boolean {
        return protected.containsKey(key) || probation.containsKey(key)
    }

    override fun remove(key: K): V? {
        return when {
            protected.containsKey(key) -> protected.remove(key)
            probation.containsKey(key) -> probation.remove(key)
            else -> null
        }
    }

    override fun count(): Long {
        return protected.count() + probation.count()
    }

    override fun eldestKey(): K? {
        return probation.eldestKey() ?: protected.eldestKey()
    }

    override fun sync() {
        protected.sync()
        probation.sync()
    }

    override fun addEldestRemovedListener(listener: EldestRemovedListener<K, V>) {
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

    override fun setKeyWeightSupplier(supplier: (k: K, v: V) -> Long) {
        protected.setKeyWeightSupplier(supplier)
        probation.setKeyWeightSupplier(supplier)
    }

    protected class ProtectedLRUEldestRemovedListener<K, V>(
        private val probationLru: StorageCacheIndexer<K, V>
    ) : EldestRemovedListener<K, V> {
        override fun onEldestRemoved(key: K, value: V) {
            probationLru.put(key, value)
        }
    }

    protected class ProbationLRUEldestRemovedListener<K, V>(
        private val parentListeners: List<EldestRemovedListener<K, V>>,
    ) : EldestRemovedListener<K, V> {
        override fun onEldestRemoved(key: K, value: V) {
            parentListeners.forEach { it.onEldestRemoved(key, value) }
        }
    }

    companion object {
        const val FACTOR_PROBATION = 0.2
        const val FACTOR_PROTECTED = 0.8
    }
}
