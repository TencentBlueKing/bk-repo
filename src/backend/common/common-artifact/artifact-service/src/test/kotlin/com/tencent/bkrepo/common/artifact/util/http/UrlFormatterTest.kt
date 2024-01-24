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

package com.tencent.bkrepo.common.artifact.util.http

import com.tencent.bkrepo.common.api.constant.StringPool
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

internal class UrlFormatterTest {

    @Test
    fun buildUrl() {
        val url = ""
        val path = StringPool.EMPTY
        val params = StringPool.EMPTY
        try {
            val result = UrlFormatter.buildUrl(url, path, params)
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Url should not be blank"))
        }

    }

    @Test
    fun buildUrl1() {
        val url = "bkrepo.example.com"
        val path = StringPool.EMPTY
        val params = StringPool.EMPTY
        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com", result)}
        )
    }

    @Test
    fun buildUrl2() {
        val url = "http://bkrepo.example.com"
        val path = StringPool.EMPTY
        val params = StringPool.EMPTY
        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com", result)}
        )
    }

    @Test
    fun buildUrl3() {
        val url = "http://bkrepo.example.com/"
        val path = StringPool.EMPTY
        val params = StringPool.EMPTY
        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com", result)}
        )
    }

    @Test
    fun buildUrl4() {
        val url = "http://bkrepo.example.com//"
        val path = "/v2/"
        val params = StringPool.EMPTY
        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/v2/", result)}
        )
    }

    @Test
    fun buildUrl5() {
        val url = "http://bkrepo.example.com//"
        val path = "v2"
        val params = StringPool.EMPTY
        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/v2", result)}
        )
    }

    @Test
    fun buildUrl6() {
        val url = "http://bkrepo.example.com//"
        val path = "v2"
        val params = "a=a"
        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/v2?a=a", result)}
        )
    }

    @Test
    fun buildUrl7() {
        val url = "http://bkrepo.example.com/?b=b"
        val path = "v2"
        val params = "a=a"

        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/v2?b=b&a=a", result)}
        )
    }

    @Test
    fun buildUrl8() {
        val url = "http://bkrepo.example.com/test/"
        val path = "/v2"

        val result = UrlFormatter.buildUrl(url, path)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/v2", result)}
        )
    }

    @Test
    fun buildUrl9() {
        val url = "http://bkrepo.example.com/test/"
        val path = "/v2/"

        val result = UrlFormatter.buildUrl(url, path)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/v2/", result)}
        )
    }

    @Test
    fun buildUrl10() {
        val url = "http://bkrepo.example.com/test/"
        val path = "/v2/"
        val params = "a=a"
        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/v2/?a=a", result)}
        )
    }

    @Test
    fun buildUrl11() {
        val url = "http://bkrepo.example.com/test/?b=b"
        val path = "/v2/"
        val params = "a=a"
        val result = UrlFormatter.buildUrl(url, path, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/v2/?b=b&a=a", result)}
        )
    }

    @Test
    fun addParams() {
        val url = "http://bkrepo.example.com/test/?b=b"
        val params = "a=a"
        val result = UrlFormatter.addParams(url, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/?b=b&a=a", result)}
        )
    }

    @Test
    fun addParams1() {
        val url = "http://bkrepo.example.com/test/?b=b"
        val params = ""
        val result = UrlFormatter.addParams(url, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/?b=b", result)}
        )
    }

    @Test
    fun addParams2() {
        val url = "http://bkrepo.example.com/test/"
        val params = "b=b"
        val result = UrlFormatter.addParams(url, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/?b=b", result)}
        )
    }

    @Test
    fun addParams3() {
        val url = "http://bkrepo.example.com/test"
        val params = "b=b"
        val result = UrlFormatter.addParams(url, params)
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test?b=b", result)}
        )
    }



    @Test
    fun addProtocol() {
        val url = "bkrepo.example.com/test/"
        val result = UrlFormatter.addProtocol(url).toString()
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/", result)}
        )
    }

    @Test
    fun addProtocol1() {
        val url = "http://bkrepo.example.com/test/"
        val result = UrlFormatter.addProtocol(url).toString()
        assertAll(
            {Assertions.assertEquals("http://bkrepo.example.com/test/", result)}
        )
    }

    @Test
    fun addProtocol2() {
        val url = "https://bkrepo.example.com/test/"
        val result = UrlFormatter.addProtocol(url).toString()
        assertAll(
            {Assertions.assertEquals("https://bkrepo.example.com/test/", result)}
        )
    }

    @Test
    fun addProtocol3() {
        val url = "bkrepo.example.com:test/"
        try {
            val result = UrlFormatter.addProtocol(url).toString()
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Check your input url!"))
        }
    }
}