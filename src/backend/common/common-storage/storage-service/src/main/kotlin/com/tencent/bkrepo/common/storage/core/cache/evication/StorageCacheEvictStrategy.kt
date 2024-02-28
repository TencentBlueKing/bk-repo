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

/**
 * 存储缓存淘汰策略，维护一份硬盘缓存文件的索引，根据特定策略对索引进行淘汰同时删除硬盘上的缓存文件
 */
interface StorageCacheEvictStrategy<K, V> {
    /**
     * 添加缓存记录
     *
     * @param key 缓存key，默认为缓存文件sha256
     * @param value 缓存值，默认为缓存文件大小
     * @param score 缓存优先级，用于缓存淘汰决策，默认为当前时间戳
     */
    fun put(key: K, value: V, score: Double? = System.currentTimeMillis().toDouble()): V?

    /**
     * 获取缓存值
     *
     * @param key 缓存key
     */
    fun get(key: K): V?

    /**
     * 维护的索引中是否包含缓存
     *
     * @param key 缓存key
     */
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

    /**
     * 设置缓存最大权重，超过权重后开始执行缓存淘汰直到小于最大权重，为0时表示无限制
     *
     * @param max 最大权重值
     */
    fun setMaxWeight(max: Long)

    /**
     * 获取最大权重
     *
     * @return 最大权重
     */
    fun getMaxWeight(): Long

    /**
     * 设置缓存容量，为0时表示无限制
     *
     * @param capacity 缓存容量
     */
    fun setCapacity(capacity: Int)

    /**
     * 获取缓存容量
     *
     * @return 缓存容量
     */
    fun getCapacity(): Int

    /**
     * 设置缓存条目权重计算方式
     *
     * @param supplier 用于计算缓存条目权重
     */
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

    /**
     * 添加缓存淘汰监听器
     *
     * @param listener 缓存淘汰监听器
     */
    fun addEldestRemovedListener(listener: EldestRemovedListener<K, V>)

    /**
     * 获取缓存淘汰监听器
     *
     * @return 所有缓存淘汰监听器
     */
    fun getEldestRemovedListeners(): List<EldestRemovedListener<K, V>>
}

/**
 * 缓存淘汰回调
 */
interface EldestRemovedListener<K, V> {
    /**
     * 缓存淘汰回调
     *
     * @param key 被淘汰的缓存key
     * @param value 被淘汰的缓存value
     */
    fun onEldestRemoved(key: K, value: V)
}
