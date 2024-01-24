/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.lock.service

interface CasService {
    fun increment(key: String, delta: Long): Long

    fun get(key: String): Long

    fun delete(key: String)

    /**
     * 判断key 对应的值是否被清零
     */
    fun targetCheck(
        key: String,
        target: Long = 0,
        retryTimes: Int = RETRY_TIMES,
        sleepTime: Long = SPIN_SLEEP_TIME
    ): Boolean {
        // 自旋获取锁
        for (i in 0 until retryTimes) {
            val result = get(key)
            when (result <= target) {
                true -> return true
                else ->
                    try {
                        Thread.sleep(sleepTime)
                    } catch (ignore: InterruptedException) {
                    }
            }
        }
        return false
    }

    companion object {
        const val SPIN_SLEEP_TIME: Long = 30L
        const val RETRY_TIMES: Int = 10000
    }
}