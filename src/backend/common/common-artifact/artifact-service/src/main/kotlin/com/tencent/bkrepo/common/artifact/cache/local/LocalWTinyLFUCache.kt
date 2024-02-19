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

/**
 * 通过W-TinyLFU算法进行缓存淘汰
 * 非线程安全，仅用于单元测试与本地测试
 */
class LocalWTinyLFUCache(
    capacity: Int,
    private val counter: Counter,
    private val listeners: MutableList<EldestRemovedListener<String, Any?>> = ArrayList()
) : OrderedCache<String, Any?> {
    private val mainLRU = LocalSLRUCache((capacity * 0.99f).toInt(), listeners)
    private val edenListener = EdenLRUEldestRemovedListener(mainLRU, counter, listeners)
    private val edenLRU = LocalLRUCache((capacity * 0.01f).toInt(), mutableListOf(edenListener))

    override fun put(key: String, value: Any?): Any? {
        counter.inc(key)
        return if (mainLRU.containsKey(key)) {
            mainLRU.put(key, value)
        } else {
            edenLRU.put(key, value)
        }
    }

    override fun get(key: String): Any? {
        counter.inc(key)
        return mainLRU.get(key) ?: edenLRU[key]
    }

    override fun containsKey(key: String): Boolean {
        return mainLRU.containsKey(key) || edenLRU.containsKey(key)
    }

    override fun remove(key: String): Any? {
        return if (mainLRU.containsKey(key)) {
            mainLRU.remove(key)
        } else {
            edenLRU.remove(key)
        }
    }

    override fun count(): Long {
        return mainLRU.count() + edenLRU.count()
    }

    override fun weight(): Long = edenLRU.weight() + mainLRU.weight()

    override fun setMaxWeight(max: Long) {
        mainLRU.setMaxWeight((max * FACTOR_MAIN).toLong())
        edenLRU.setMaxWeight((max * FACTOR_EDEN).toLong())
    }

    override fun getMaxWeight() = mainLRU.getMaxWeight() + edenLRU.getMaxWeight()

    override fun setCapacity(capacity: Int) {
        mainLRU.setCapacity((capacity * FACTOR_MAIN).toInt())
        edenLRU.setCapacity((capacity * FACTOR_EDEN).toInt())
    }

    override fun getCapacity() = mainLRU.getCapacity() + edenLRU.getCapacity()

    override fun setKeyWeightSupplier(supplier: (k: String, v: Any?) -> Long) {
        mainLRU.setKeyWeightSupplier(supplier)
        edenLRU.setKeyWeightSupplier(supplier)
    }

    override fun eldestKey(): String? {
        return edenLRU.eldestKey() ?: mainLRU.eldestKey()
    }

    override fun addEldestRemovedListener(listener: EldestRemovedListener<String, Any?>) {
        listeners.add(listener)
    }

    override fun getEldestRemovedListeners() = listeners

    private class EdenLRUEldestRemovedListener(
        private val mainLRU: LocalSLRUCache,
        private val counter: Counter,
        private val parentListener: List<EldestRemovedListener<String, Any?>>
    ) : EldestRemovedListener<String, Any?> {
        override fun onEldestRemoved(key: String, value: Any?) {
            if (mainLRU.probationFull()) {
                // probation满时last一定不为NULL
                if (admit(mainLRU.eldestKey()!!, key)) {
                    mainLRU.put(key, value)
                } else {
                    parentListener.forEach { it.onEldestRemoved(key, value) }
                }
            } else {
                mainLRU.put(key, value)
            }
        }

        private fun admit(victimKey: String, attackerKey: String): Boolean {
            val victimCount = counter.get(victimKey)
            val attackerCount = counter.get(attackerKey)
            return if (attackerCount > victimCount) {
                return  true
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
        private const val FACTOR_EDEN = 0.01
        private const val FACTOR_MAIN = 0.99
    }
}
