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

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials

/**
 * 缓存清理器，用于记录缓存访问情况，在缓存达到限制大小时清理存储层缓存
 */
interface StorageCacheEvictor {
    /**
     *  缓存被访问时的回调，用于缓存清理决策
     *
     *  @param credentials 缓存所在存储
     *  @param sha256 缓存文件sha256
     *  @param size 缓存文件大小
     */
    fun onCacheAccessed(credentials: StorageCredentials, sha256: String, size: Long)

    /**
     * 存储层缓存被删除时调用
     *
     * @param credentials 缓存所在的存储
     * @param sha256 被删除的缓存文件的sha256

     */
    fun onCacheDeleted(credentials: StorageCredentials, sha256: String)

    /**
     * 缓存未被删除时调用，用于将不包含在缓存清理器内的条目添加到清理器内
     * @param credentials 缓存所在的存储
     * @param sha256 被删除的缓存文件的sha256
     * @param size 文件大小
     * @param score 缓存优先级，用于缓存淘汰决策
     */
    fun onCacheReserved(credentials: StorageCredentials, sha256: String, size: Long, score: Double)

    /**
     * 同步缓存清理器内维护的索引与实际缓存文件条目
     */
    fun sync(credentials: StorageCredentials)
}