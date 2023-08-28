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

package com.tencent.bkrepo.common.storage.core

import com.tencent.bk.sdk.crypto.util.SM4InputStream
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.storage.core.crypto.SM4EncryptInputStream
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SM4EncryptInputStreamTest {
    @Test
    fun encryptTest() {
        val plainText = StringPool.randomString(10)
        val key = "key"

        // 加密
        val encryptInputStream = SM4EncryptInputStream(plainText.byteInputStream(), key)
        val outputStream = ByteArrayOutputStream()
        encryptInputStream.copyTo(outputStream)
        // 解密
        val sM4InputStream = SM4InputStream(ByteArrayInputStream(outputStream.toByteArray()), key)
        val output = ByteArrayOutputStream()
        sM4InputStream.copyTo(output)
        Assertions.assertEquals(plainText, String(output.toByteArray()))
    }
}
