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

package com.tencent.bkrepo.common.storage.core.cache.evication

interface StorageCacheEvictStrategy<K, V> {
    /**
     * 添加缓存记录
     *
     * @param key 缓存key，默认为缓存文件sha256
     * @param value 缓存值，默认为缓存文件大小
     * @param score 缓存优先级，用于缓存淘汰决策，默认为当前时间戳
     */
    fun put(key: K, value: V, score: Double? = System.currentTimeMillis().toDouble()): V?
    fun get(key: K): V?
    fun containsKey(key: K): Boolean

    /**
     * 移除缓存，需要调用该方法移除缓存，其他手段移除缓存可能导致weight统计异常
     *
     * @param key key
     * @return 缓存值，不存在时返回NULL
     */
    fun remove(key: K): V?

    /**
     * 获取缓存数量
     */
    fun count(): Long

    /**
     * 获取缓存当前总权重
     */
    fun weight(): Long

    fun setMaxWeight(max: Long)

    fun getMaxWeight(): Long

    fun setCapacity(capacity: Int)

    fun getCapacity(): Int

    fun setKeyWeightSupplier(supplier: (k: K, v: V) -> Long)

    /**
     * 获取最旧的key
     */
    fun eldestKey(): K?

    /**
     * 同步策略中存储的缓存索引条目与实际磁盘缓存条目
     */
    fun sync()

    /**
     * 缓存是否已满
     */
    fun full(): Boolean =
        getCapacity() > 0L && count() >= getCapacity() || getMaxWeight() > 0L && weight() >= getMaxWeight()

    fun addEldestRemovedListener(listener: EldestRemovedListener<K, V>)

    fun getEldestRemovedListeners(): List<EldestRemovedListener<K, V>>
}

interface EldestRemovedListener<K, V> {
    fun onEldestRemoved(key: K, value: V)
}
