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

package com.tencent.bkrepo.common.ratelimiter.utils

import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourcePathUtilsTest {

    @Test
    fun testTokenizeUrlPath() {
        val url = "/test1/test2"
        try {
            val actualSegments = ResourcePathUtils.tokenizeResourcePath(url)
            assertEquals(actualSegments.size, 2)
            MatcherAssert.assertThat(actualSegments, CoreMatchers.hasItems("test1", "test2"))
        } catch (e: InvalidResourceException) {
            fail("tokenizeUrlPath should not throw InvalidResourceException.")
        }
    }

    @Test
    fun testTokenizeUrlPathWithUrlPatten() {
        val url = "/test1/test2/{projectId}/{repoName}/{*:(.*)}"
        try {
            val actualSegments = ResourcePathUtils.tokenizeResourcePath(url)
            assertEquals(actualSegments.size, 5)
            MatcherAssert.assertThat(
                actualSegments,
                CoreMatchers.hasItems("test1", "test2", "{projectId}", "{repoName}", "{*:(.*)}")
            )
        } catch (e: InvalidResourceException) {
            fail("tokenizeUrlPath should not throw InvalidResourceException.")
        }
    }

    @Test
    fun testTokenizeUrlPathWithEmptyUrl() {
        try {
            val actualSegments = ResourcePathUtils.tokenizeResourcePath("")
            assertNotNull(actualSegments)
            assertEquals(actualSegments.size, 0)
            val actualSegments2 = ResourcePathUtils.tokenizeResourcePath("/")
            assertNotNull(actualSegments2)
            assertEquals(actualSegments2.size, 0)
        } catch (e: InvalidResourceException) {
            fail("tokenizeUrlPath should not throw InvalidResourceException.")
        }
    }

    @Test
    fun `test tokenizeResourcePath with URL encoded path`() {
        val path = "/a%2Fb"
        val result = ResourcePathUtils.tokenizeResourcePath(path)
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `test tokenizeResourcePath with consecutive slashes`() {
        val path = "/a//b"
        val result = ResourcePathUtils.tokenizeResourcePath(path)
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `test tokenizeResourcePath with mixed path`() {
        val path = "/a%2Fb//c"
        val result = ResourcePathUtils.tokenizeResourcePath(path)
        assertEquals(listOf("a", "b", "c"), result)
    }


    @Test
    fun `test tokenizeResourcePath with consecutive URL encoded path`() {
        val path = "%2Fa%2Fb%2F%2Fc"
        val result = ResourcePathUtils.tokenizeResourcePath(path)
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun testGetUrlPath() {
        try {
            var actualPath: String? = ResourcePathUtils.getPathOfUrl("http://www.bkrepo.com/")
            assertEquals(actualPath, "/")
            actualPath = ResourcePathUtils.getPathOfUrl("http://www.bkrepo.com")
            assertEquals(actualPath, "/")
            actualPath = ResourcePathUtils.getPathOfUrl("http://www.bkrepo.com/test1/test2")
            assertEquals(actualPath, "/test1/test2")
            actualPath = ResourcePathUtils.getPathOfUrl("http://www.bkrepo.com/test1/test2?user=xxx")
            assertEquals(actualPath, "/test1/test2")
            actualPath = ResourcePathUtils.getPathOfUrl("/test1/test2")
            assertEquals(actualPath, "/test1/test2")
            actualPath = ResourcePathUtils.getPathOfUrl("/test1/test2?user=xxx")
            assertEquals(actualPath, "/test1/test2")
            actualPath = ResourcePathUtils.getPathOfUrl("/test1/test2/")
            assertEquals(actualPath, "/test1/test2/")
        } catch (e: InvalidResourceException) {
            fail("getPathOfUrl() should not throw exception here.")
        }
    }

    @Test
    fun testGetUrlPathWithEmptyUrl() {
        try {
            val actualPath: String? = ResourcePathUtils.getPathOfUrl("")
            assertNull(actualPath)
        } catch (e: InvalidResourceException) {
            fail("getPathOfUrl() should not throw exception here.")
        }
    }

    @Test
    fun testGetUserAndPathWithEmptyUrl() {
        try {
            assertThrows<InvalidResourceException> { ResourcePathUtils.getUserAndPath("") }
        } catch (e: InvalidResourceException) {
            fail("getUserAndPath() should not throw exception here.")
        }
    }

    @Test
    fun testGetUserAndPath() {
        try {
            assertThrows<InvalidResourceException> { ResourcePathUtils.getUserAndPath("a") }
            var (userId, path) = ResourcePathUtils.getUserAndPath("a:")
            assertEquals(userId, "a")
            assertEquals(path, "")
            val (userId1, path1) = ResourcePathUtils.getUserAndPath("a:1")
            assertEquals(userId1, "a")
            assertEquals(path1, "1")
            val (userId2, path2) = ResourcePathUtils.getUserAndPath("a:1:12")
            assertEquals(userId2, "a")
            assertEquals(path2, "1:12")
        } catch (e: InvalidResourceException) {
            fail("getPathOfUrl() should not throw exception here.")
        }
    }

    @Test
    fun testBuildUserPath() {
        var actualPath: String? = ResourcePathUtils.buildUserPath("a", "b")
        assertEquals(actualPath, "a:b")
        actualPath = ResourcePathUtils.buildUserPath("1", "2")
        assertEquals(actualPath, "1:2")
    }

    @Test
    fun testBuildUserPathWithEmptyUrl() {
        var actualPath: String? = ResourcePathUtils.buildUserPath("a", "")
        assertEquals(actualPath, "a:")
    }
}