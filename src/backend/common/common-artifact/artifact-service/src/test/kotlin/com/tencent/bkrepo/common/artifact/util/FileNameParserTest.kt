/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FileNameParserTest {
    @Test
    fun testParser() {
        val list = listOf(
            "/bcs-general-pod-autoscaler-v1.26.0-alpha.1.tgz", "/bcs-gamestatefulset-operator-1.26.0-alpha.1.tgz",
            "/bcs-gamestatefulset-operator-1.tgz", "/bcs-gamestatefulset-operator-v1.tgz",
            "/bcs-gamestatefulset-operator-1.2.tgz", "/bcs-gamestatefulset-operator-1.2.2.tgz",
            "/bcs-gamestatefulset-operator-v1.2.tgz", "/bcs-gamestatefulset-operator-v1.2.2.tgz",
            "/artifactory-ha-3.0.1400.tgz"
        )
        list.forEach {
            val map1 = FileNameParser.parseNameAndVersion(it)
            val map2 = FileNameParser.parseNameAndVersionWithRegex(it)
            println("$map1 | $map2")
            Assertions.assertEquals(map1, map2)
        }
    }
}
