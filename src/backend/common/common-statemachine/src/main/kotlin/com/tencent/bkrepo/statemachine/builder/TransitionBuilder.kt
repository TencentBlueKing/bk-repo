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

package com.tencent.bkrepo.statemachine.builder

import com.tencent.bkrepo.statemachine.Action
import com.tencent.bkrepo.statemachine.Condition
import com.tencent.bkrepo.statemachine.Transition

class TransitionBuilder {
    private val sources: MutableSet<String> = LinkedHashSet()
    private var target: String? = null
    private var event: String? = null
    private var action: Action? = null
    private var condition: Condition? = null

    /**
     * 设置状态转移过程源状态
     */
    fun source(source: String) {
        this.sources.add(source)
    }

    /**
     * 批量设置状态转移过程源状态
     */
    fun sources(vararg sources: String) {
        this.sources.addAll(sources)
    }

    /**
     * 设置状态转移过程目标状态
     */
    fun target(target: String) {
        this.target = target
    }

    /**
     * 设置状态转移过程触发事件
     */
    fun event(event: String) {
        this.event = event
    }

    /**
     * 设置状态转移过程触发条件
     */
    fun condition(condition: Condition) {
        this.condition = condition
    }

    /**
     * 设置状态转移过程
     */
    fun action(action: Action) {
        this.action = action
    }

    fun build(): List<Transition> {
        check(sources.isNotEmpty()) { "source states is empty" }
        checkNotNull(target) { "target is null" }
        checkNotNull(event) { "event is null" }
        checkNotNull(action) { "action is null" }

        return sources.map { Transition(it, target!!, event!!, action!!, condition) }
    }
}
