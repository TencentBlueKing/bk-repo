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

package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class RateLimiterInterceptorChainTest {

    open class InterceptorA : RateLimiterInterceptor {

        override fun beforeLimitCheck(resource: String) {
            list.add(identity() + ":before")
        }


        override fun afterLimitCheck(
            resource: String, resourceLimit: ResourceLimit?,
            result: Boolean, e: Exception?, applyPermits: Long
        ) {
            list.add(identity() + ":after")
        }

        protected open fun identity(): String {
            return InterceptorA::class.java.simpleName
        }
    }

    class InterceptorB : InterceptorA() {
        override fun identity(): String {
            return InterceptorB::class.java.simpleName
        }
    }

    class InterceptorC : InterceptorA() {
        override fun identity(): String {
            return InterceptorC::class.java.simpleName
        }
    }

    @BeforeEach
    fun reInit() {
        list.clear()
    }

    @Test
    fun testDoBeforeLimit() {
        val chain = RateLimiterInterceptorChain()
        chain.addInterceptor(InterceptorA())
        chain.addInterceptor(InterceptorB())
        chain.addInterceptor(InterceptorC())
        chain.doBeforeLimitCheck("test1")
        assertEquals(Companion.list.size, 3)
        assertEquals(Companion.list[0], "InterceptorA:before")
        assertEquals(Companion.list[1], "InterceptorB:before")
        assertEquals(Companion.list[2], "InterceptorC:before")
    }

    @Test
    fun testDoAfterLimit() {
        val chain = RateLimiterInterceptorChain()
        chain.addInterceptor(InterceptorC())
        chain.addInterceptor(InterceptorB())
        chain.addInterceptor(InterceptorA())
        chain.doAfterLimitCheck("test1", null, true, null, 1)
        assertEquals(Companion.list.size, 3)
        assertEquals(Companion.list[0], "InterceptorC:after")
        assertEquals(Companion.list[1], "InterceptorB:after")
        assertEquals(Companion.list[2], "InterceptorA:after")
    }

    @Test
    fun testIsEmpty() {
        val chain = RateLimiterInterceptorChain()
        val isEmpty = chain.isEmpty()
        Assertions.assertTrue(isEmpty)
    }

    @Test
    fun testClear() {
        val chain = RateLimiterInterceptorChain()
        chain.addInterceptor(InterceptorB())
        chain.addInterceptor(InterceptorA())
        chain.addInterceptor(InterceptorC())
        assertEquals(chain.size(), 3)
        chain.clear()
        assertEquals(chain.size(), 0)
    }

    companion object {
        val list: MutableList<String> = ArrayList()
    }
}
