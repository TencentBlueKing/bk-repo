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

import org.junit.jupiter.api.Test
import com.tencent.bkrepo.statemachine.builder.StateMachineBuilder.Companion.stateMachine
import org.junit.jupiter.api.Assertions

class StateMachineTest {

    enum class TestState { INIT, EXECUTING, SUCCESS, FAILED }
    enum class TestEvent { EXECUTE, FINISH }
    data class TestContext(val result: String = ""): Context

    @Suppress("LongMethod")
    @Test
    fun testSendEvent() {
        // 创建状态机
        val stateMachine = stateMachine("test") {
            addTransition {
                source(TestState.INIT.name)
                target(TestState.EXECUTING.name)
                event(TestEvent.EXECUTE.name)
                action { source, target, event ->
                    println("transit from $source to $target")
                    TransitResult(TestState.EXECUTING.name)
                }
            }
            addTransition {
                source(TestState.EXECUTING.name)
                target(TestState.SUCCESS.name)
                event(TestEvent.FINISH.name)
                action { source, target, event ->
                    val context = event.context
                    require(context is TestContext)
                    println("transit from $source to $target")
                    TransitResult(TestState.SUCCESS.name, context.result)
                }
                condition { _, _, event ->
                    val context = event.context
                    require(context is TestContext)
                    context.result == TestState.SUCCESS.name
                }
            }
            addTransition {
                sources(TestState.INIT.name, TestState.EXECUTING.name)
                target(TestState.FAILED.name)
                event(TestEvent.FINISH.name)
                action { source, target, event ->
                    val context = event.context
                    require(context is TestContext)
                    println("transit from $source to $target")
                    TransitResult(TestState.FAILED.name, context.result)
                }
                condition { _, _, event ->
                    val context = event.context
                    require(context is TestContext)
                    context.result == TestState.FAILED.name
                }
            }
        }

        // INIT -> EXECUTING
        var result = stateMachine.sendEvent(
            TestState.INIT.name,
            Event(TestEvent.EXECUTE.name, TestContext(""))
        )
        Assertions.assertEquals(TestState.EXECUTING.name, result.transitState)
        Assertions.assertNull(result.result)

        // EXECUTING -> SUCCESS
        result = stateMachine.sendEvent(
            TestState.EXECUTING.name,
            Event(TestEvent.FINISH.name, TestContext(TestState.SUCCESS.name))
        )
        Assertions.assertEquals(TestState.SUCCESS.name, result.transitState)
        Assertions.assertEquals(TestState.SUCCESS.name, result.result)

        // EXECUTING -> FAILED
        result = stateMachine.sendEvent(
            TestState.EXECUTING.name,
            Event(TestEvent.FINISH.name, TestContext(TestState.FAILED.name))
        )
        Assertions.assertEquals(TestState.FAILED.name, result.transitState)
        Assertions.assertEquals(TestState.FAILED.name, result.result)


        // INIT -> FAILED
        result = stateMachine.sendEvent(
            TestState.INIT.name,
            Event(TestEvent.FINISH.name, TestContext(TestState.FAILED.name))
        )
        Assertions.assertEquals(TestState.FAILED.name, result.transitState)
        Assertions.assertEquals(TestState.FAILED.name, result.result)
    }

    /**
     * 存在condition的transition优先级高于无condition的
     */
    @Test
    fun testTransitionPriority1() {
        val stateMachine = stateMachine("test") {
            addTransition {
                source(TestState.INIT.name)
                target(TestState.SUCCESS.name)
                event(TestEvent.FINISH.name)
                action { source, target, event ->
                    println("transit from $source to $target")
                    TransitResult(TestState.SUCCESS.name, TestState.SUCCESS.name)
                }
            }
            addTransition {
                source(TestState.INIT.name)
                target(TestState.FAILED.name)
                event(TestEvent.FINISH.name)
                action { source, target, event ->
                    println("transit from $source to $target")
                    TransitResult(TestState.FAILED.name, TestState.FAILED.name)
                }
                condition { _, _, event ->
                    val context = event.context
                    require(context is TestContext)
                    context.result == TestState.FAILED.name
                }
            }
        }

        val event = Event(TestEvent.FINISH.name, TestContext(TestState.FAILED.name))
        val result = stateMachine.sendEvent(TestState.INIT.name, event)
        Assertions.assertEquals(TestState.FAILED.name, result.result)
    }

    /**
     * 多个存在condition的transition匹配时，根据声明顺序，第一个优先
     */
    @Test
    fun testTransitionPriority2() {
        val stateMachine = stateMachine("test") {
            addTransition {
                source(TestState.INIT.name)
                target(TestState.SUCCESS.name)
                event(TestEvent.FINISH.name)
                action { source, target, event ->
                    println("transit from $source to $target")
                    TransitResult(TestState.SUCCESS.name, TestState.SUCCESS.name)
                }
                condition { _, _, _ -> true}
            }
            addTransition {
                source(TestState.INIT.name)
                target(TestState.FAILED.name)
                event(TestEvent.FINISH.name)
                action { source, target, event ->
                    println("transit from $source to $target")
                    TransitResult(TestState.FAILED.name, TestState.FAILED.name)
                }
                condition { _, _, _ -> true}
            }
        }

        val event = Event(TestEvent.FINISH.name, TestContext())
        val result = stateMachine.sendEvent(TestState.INIT.name, event)
        Assertions.assertEquals(TestState.SUCCESS.name, result.result)
    }

    /**
     * 多个无condition的transition匹配时，根据声明顺序，第一个优先
     */
    @Test
    fun testTransitionPriority3() {
        val stateMachine = stateMachine("test") {
            addTransition {
                source(TestState.INIT.name)
                target(TestState.SUCCESS.name)
                event(TestEvent.FINISH.name)
                action { source, target, event ->
                    println("transit from $source to $target")
                    TransitResult(TestState.SUCCESS.name, TestState.SUCCESS.name)
                }
            }
            addTransition {
                source(TestState.INIT.name)
                target(TestState.FAILED.name)
                event(TestEvent.FINISH.name)
                action { source, target, event ->
                    println("transit from $source to $target")
                    TransitResult(TestState.FAILED.name, TestState.FAILED.name)
                }
            }
        }

        val event = Event(TestEvent.FINISH.name, TestContext())
        val result = stateMachine.sendEvent(TestState.INIT.name, event)
        Assertions.assertEquals(TestState.SUCCESS.name, result.result)
    }

    @Test
    fun testNoTransition() {
        val stateMachine = stateMachine("test") {
            addTransition {
                source(TestState.INIT.name)
                target(TestState.EXECUTING.name)
                event(TestEvent.EXECUTE.name)
                action { source, target, event ->
                    println("transit from $source to $target")
                    TransitResult(TestState.EXECUTING.name)
                }
                condition { _, _, _ -> true}
            }
        }

        val result = stateMachine.sendEvent(TestState.INIT.name, Event(TestEvent.FINISH.name, TestContext()))
        Assertions.assertEquals(TestState.INIT.name, result.transitState)
    }
}
