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

import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.StateMachineImpl

class StateMachineBuilder private constructor(private val name: String) {
    private val stateBuilders: MutableMap<String, StateBuilder> = HashMap()

    /**
     * 添加状态转移过程
     * 
     * @param configuration 状态转移过程配置
     */
    fun addTransition(configuration: TransitionBuilder.() -> Unit) {
        val builder = TransitionBuilder()
        builder.configuration()
        builder.build().forEach { 
            getOrCreateStateBuilder(it.source).addTransition(it.event, it)
        }
    }

    private fun build(): StateMachine {
        return StateMachineImpl(name, stateBuilders.mapValues { it.value.build() })
    }
    
    private fun getOrCreateStateBuilder(name: String): StateBuilder {
        return stateBuilders.getOrPut(name) { StateBuilder(name) }
    }

    companion object {
        /**
         * 创建状态机
         * 
         * @param name 状态机名
         * @param configuration 状态机配置
         * 
         * @return 创建后的状态机
         */
        fun stateMachine(name: String, configuration: StateMachineBuilder.() -> Unit): StateMachine {
            val builder = StateMachineBuilder(name)
            builder.configuration()
            return builder.build()
        }
    }
}
