/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.frpc.event.call

import com.tencent.bkrepo.common.frpc.event.AckEvent
import com.tencent.bkrepo.common.frpc.event.Event
import com.tencent.bkrepo.common.frpc.event.EventBus
import com.tencent.bkrepo.common.frpc.event.handler.AckEventHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 支持响应的调用
 * */
abstract class AckCall<T : Event, A : AckEvent>(eventBus: EventBus, val timeout: Long) :
    SimpleEventCall<T>(eventBus),
    AckEventHandler<A> {
    /**
     * 请求上下文map
     * */
    private val contextMap = ConcurrentHashMap<String, AckCallContext<A>>()

    /**
     * 响应是否完成
     * @param acks 收到的响应
     * @return true完成响应，否则返回false
     * */
    abstract fun isComplete(acks: List<A>): Boolean
    override fun call(event: T) {
        val context = initContext(event)
        contextMap[event.id] = context
        try {
            super.call(event)
            if (isComplete(context.acks)) {
                return
            }
            waitForAck(context)
        } finally {
            contextMap.remove(event.id)
        }
    }

    /**
     * 等待响应
     *
     * 达到响应返回，或者超时返回
     * */
    private fun waitForAck(context: AckCallContext<A>) {
        context.lock.withLock {
            val success = context.condition.await(timeout, TimeUnit.MILLISECONDS)
            if (!success) {
                // 超时
                throw TimeoutException("Call timeout")
            }
        }
    }

    override fun ack(event: A) {
        val context = contextMap[event.id]
        // 非调用者，不处理响应。
        if (context == null) {
            logger.trace("No context for ${event.id}.")
            return
        }
        val acks = context.acks
        acks.add(event)
        context.lock.withLock {
            if (isComplete(acks)) {
                context.condition.signalAll()
            }
        }
    }

    /**
     * 初始化请求上下文
     * */
    private fun initContext(event: Event): AckCallContext<A> {
        val lock = ReentrantLock()
        return AckCallContext(
            id = event.id,
            lock = lock,
            condition = lock.newCondition(),
            acks = mutableListOf()
        )
    }

    /**
     * 响应调用的上下文
     * */
    private data class AckCallContext<T>(
        val id: String, // 事件id
        val lock: ReentrantLock, // 调用锁，用于创建condition
        val condition: Condition, // 响应条件，支持阻塞和唤醒线程
        val acks: MutableList<T> // 收到的响应
    )

    companion object {
        private val logger = LoggerFactory.getLogger(AckCall::class.java)
    }
}
