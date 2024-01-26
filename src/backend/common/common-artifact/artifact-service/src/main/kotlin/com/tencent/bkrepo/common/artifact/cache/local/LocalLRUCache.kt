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

/**
 * 线程不安全
 */
class LocalLRUCache(
    private var capacity: Int = 0,
    private val listeners: MutableList<EldestRemovedListener<String, Any?>> = ArrayList(),
) : LinkedHashMap<String, Any?>((capacity / 0.75f).toInt() + 1, 0.75f, true), OrderedCache<String, Any?> {

    private var maxWeight: Long = 0L
    private var totalWeight: Long = 0L
    private var weightSupplier: ((k: String, v: Any?) -> Long) = { _, _ ->  0 }

    override fun put(key: String, value: Any?): Any? {
        totalWeight += weightSupplier.invoke(key, value)
        return super.put(key, value)
    }

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any?>): Boolean {
        val shouldRemove = shouldRemove()
        if (shouldRemove) {
            totalWeight -= weightSupplier.invoke(eldest.key, eldest.value)
            listeners.forEach { it.onEldestRemoved(eldest.key, eldest.value) }
        }
        return shouldRemove
    }

    override fun remove(key: String): Any? {
        return super.remove(key)?.also { totalWeight -= weightSupplier.invoke(key, it) }
    }

    override fun setKeyWeightSupplier(supplier: (k: String, v: Any?) -> Long) {
        weightSupplier = supplier
    }

    private fun shouldRemove() = maxWeight > 0L && totalWeight > maxWeight || capacity > 0 && size > capacity

    override fun weight() = totalWeight

    override fun setMaxWeight(max: Long) {
        maxWeight = max
    }

    override fun getMaxWeight() = maxWeight

    override fun setCapacity(capacity: Int) {
        this.capacity = capacity
    }

    override fun getCapacity() = capacity

    override fun count() = size
    override fun last(): String? {
        val field = LinkedHashMap::class.java.getDeclaredField("head")
        field.isAccessible = true
        return (field.get(this) as? Map.Entry<String, Any?>)?.key
    }

    override fun addEldestRemovedListener(listener: EldestRemovedListener<String, Any?>) {
        listeners.add(listener)
    }

    override fun getEldestRemovedListeners() = listeners
}
