/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.statemachine

/**
 * 状态转移过程
 */
class Transition(
    /**
     * 源状态
     */
    val source: String,
    /**
     * 目标状态
     */
    val target: String,
    /**
     * 状态转移触发事件
     */
    val event: String,
    /**
     * 状态转移执行的操作
     *
     * 传入的参数为源状态，目标状态，触发事件
     */
    private val action: Action,
    /**
     * 满足该条件时执行才转移
     *
     * 传入的参数为源状态，目标状态，触发事件，返回值为true时执行转移
     */
    private val condition: Condition? = null
) {
    /**
     * 触发的事件是否满足状态转移条件
     *
     * @return 是否满足状态转移条件，[condition]为null时返回true
     */
    fun match(event: Event): Boolean {
        return condition?.match(source, target, event) ?: true
    }

    /**
     * 是否有状态转移条件
     */
    fun hasCondition() = condition != null

    /**
     * 执行状态转移
     */
    fun transit(event: Event): TransitResult {
        return action.execute(source, target, event)
    }
}
