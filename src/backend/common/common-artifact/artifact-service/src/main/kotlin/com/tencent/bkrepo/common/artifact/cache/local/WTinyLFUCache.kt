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
import com.tencent.bkrepo.common.artifact.cache.EldestRemovedListener
import com.tencent.bkrepo.common.artifact.cache.OrderedCache
import java.util.concurrent.ThreadLocalRandom

abstract class WTinyLFUCache<K, V>(
    private val counter: Counter,
    private val listeners: MutableList<EldestRemovedListener<K, V>> = ArrayList()
) : OrderedCache<K, V> {
    protected abstract val mainLRU: OrderedCache<K, V>
    protected abstract val windowLRU: OrderedCache<K, V>

    override fun put(key: K, value: V?): V? {
        counter.inc(key.toString())
        return if (mainLRU.containsKey(key)) {
            mainLRU.put(key, value)
        } else {
            windowLRU.put(key, value)
        }
    }

    override fun get(key: K): V? {
        counter.inc(key.toString())
        return mainLRU.get(key) ?: windowLRU.get(key)
    }

    override fun containsKey(key: K): Boolean {
        return mainLRU.containsKey(key) || windowLRU.containsKey(key)
    }

    override fun remove(key: K): V? {
        return if (mainLRU.containsKey(key)) {
            mainLRU.remove(key)
        } else {
            windowLRU.remove(key)
        }
    }

    override fun count(): Long {
        return mainLRU.count() + windowLRU.count()
    }

    override fun weight(): Long = windowLRU.weight() + mainLRU.weight()

    override fun setMaxWeight(max: Long) {
        mainLRU.setMaxWeight((max * FACTOR_MAIN).toLong())
        windowLRU.setMaxWeight((max * FACTOR_WINDOW).toLong())
    }

    override fun getMaxWeight() = mainLRU.getMaxWeight() + windowLRU.getMaxWeight()

    override fun setCapacity(capacity: Int) {
        mainLRU.setCapacity((capacity * FACTOR_MAIN).toInt())
        windowLRU.setCapacity((capacity * FACTOR_WINDOW).toInt())
    }

    override fun getCapacity() = mainLRU.getCapacity() + windowLRU.getCapacity()

    override fun setKeyWeightSupplier(supplier: (k: K, v: V) -> Long) {
        mainLRU.setKeyWeightSupplier(supplier)
        windowLRU.setKeyWeightSupplier(supplier)
    }

    override fun eldestKey(): K? {
        return windowLRU.eldestKey() ?: mainLRU.eldestKey()
    }

    override fun addEldestRemovedListener(listener: EldestRemovedListener<K, V>) {
        listeners.add(listener)
    }

    override fun getEldestRemovedListeners() = listeners

    protected class EdenLRUEldestRemovedListener<K, V>(
        private val mainLRU: SLRUCache<K, V>,
        private val counter: Counter,
        private val parentListener: List<EldestRemovedListener<K, V>>
    ) : EldestRemovedListener<K, V> {
        override fun onEldestRemoved(key: K, value: V?) {
            if (mainLRU.full()) {
                // probation满时last一定不为NULL
                if (admit(mainLRU.eldestKey()!!.toString(), key.toString())) {
                    mainLRU.put(key, value)
                } else {
                    parentListener.forEach { it.onEldestRemoved(key, value) }
                }
            } else {
                mainLRU.put(key, value)
            }
        }

        private fun admit(victimKey: String, candidateKey: String): Boolean {
            val victimCount = counter.get(victimKey)
            val attackerCount = counter.get(candidateKey)
            return if (attackerCount > victimCount) {
                return true
            } else if (attackerCount >= ADMIT_THRESHOLD) {
                // 随机选择一个淘汰
                val random = ThreadLocalRandom.current().nextInt()
                ((random and 127) == 0)
            } else {
                false
            }
        }
    }

    companion object {
        private const val ADMIT_THRESHOLD = 6
        const val FACTOR_WINDOW = 0.01f
        const val FACTOR_MAIN = 0.99f
    }
}
