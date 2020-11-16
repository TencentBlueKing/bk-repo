/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.util.XmlStrUtils.packagesModify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.util.regex.Pattern

@SpringBootTest
class XmlStrUtilsTest {
    /**
     * 按照仓库设置的repodata 深度分割请求参数
     */
    @Test
    fun splitUriByDepthTest() {
        val uri1 = "/7/os/x86_64/hello-world-1-1.x86_64.rpm"
        val depth1 = 3
        val repodataUri = XmlStrUtils.splitUriByDepth(uri1, depth1)
        Assertions.assertEquals("/7/os/x86_64/", repodataUri.repoDataPath)
        Assertions.assertEquals("hello-world-1-1.x86_64.rpm", repodataUri.artifactRelativePath)

        val uri2 = "/7/hello-world-1-1.x86_64.rpm"
        val depth2 = 1
        val repodataUri2 = XmlStrUtils.splitUriByDepth(uri2, depth2)
        Assertions.assertEquals("/7/", repodataUri2.repoDataPath)
        Assertions.assertEquals("hello-world-1-1.x86_64.rpm", repodataUri2.artifactRelativePath)

        val uri3 = "/hello-world-1-1.x86_64.rpm"
        val depth3 = 0
        val repodataUri3 = XmlStrUtils.splitUriByDepth(uri3, depth3)
        Assertions.assertEquals("/", repodataUri3.repoDataPath)
        Assertions.assertEquals("hello-world-1-1.x86_64.rpm", repodataUri3.artifactRelativePath)
    }

    @Test
    fun packagesModifyTest() {
        val start = System.currentTimeMillis()
        val file = File("/Users/weaving/Downloads/6e437f1af3f3db504cb1d2fe6d453fccb48d2b63-primary.xml")
        val resultFile = file.packagesModify(IndexType.PRIMARY, true, false)
        println(System.currentTimeMillis() - start)
        println(resultFile.absolutePath)
    }

    @Test
    fun packagesModifyTest01() {
        val regex = "^<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
            ".edu/metadata/rpm\" packages=\"(\\d+)\">$"
        val str = "<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
            ".edu/metadata/rpm\" packages=\"\">"
        val matcher = Pattern.compile(regex).matcher(str)
        if (matcher.find()) {
            println(matcher.group(1).toInt())
        }
    }
}
