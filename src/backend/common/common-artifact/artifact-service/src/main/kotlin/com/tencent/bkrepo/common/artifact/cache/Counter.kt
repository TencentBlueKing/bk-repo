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

package com.tencent.bkrepo.common.artifact.cache

/**
 * 缓存访问次数计数器
 */
interface Counter {
    /**
     * 提升计数
     *
     * @param key key
     * @param n 增加的值
     *
     * @return 提升后的值
     */
    fun incAndGet(key: String, n: Int = 1): Int

    /**
     * 计数增加1
     *
     * @param key key
     *
     * @return 如果key已存在则返回true，否则返回false
     */
    fun inc(key: String): Boolean

    /**
     * 获取计数
     *
     * @param key key
     * @return key对应的计数
     */
    fun get(key: String): Int

    /**
     * 所有计数器的值减半
     */
    fun reset()

    /**
     * 指定[key]计数减半
     *
     * @return 减半后的值
     */
    fun reset(key: String): Int

    /**
     * 清空所有计数器
     */
    fun clear()

    /**
     * 对比两个计数大小
     *
     * @param k1 key 1
     * @param k2 key 2
     *
     * return 返回值大于0时表示 k1 > k2， 返回值小于0时表示k1 < k2， 相等时返回0
     */
    fun compare(k1: String, k2: String): Int

    fun String.gt(k2: String) = compare(this, k2) > 0
}
