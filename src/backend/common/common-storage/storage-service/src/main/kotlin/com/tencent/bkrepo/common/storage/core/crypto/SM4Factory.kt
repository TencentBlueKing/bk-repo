/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.core.crypto

import com.tencent.bk.sdk.crypto.util.CipherInputStream
import com.tencent.bk.sdk.crypto.util.CipherOutputStream
import com.tencent.bk.sdk.crypto.util.SM4InputStream
import com.tencent.bk.sdk.crypto.util.SM4OutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * SM4加密工厂
 * */
class SM4Factory : AbstractCipherFactory() {
    override fun getEncryptOutputStream(outputStream: OutputStream, key: String): CipherOutputStream {
        return SM4OutputStream(outputStream, key)
    }

    override fun getPlainInputStream(inputStream: InputStream, key: String): CipherInputStream {
        return SM4InputStream(inputStream, key)
    }

    override fun getEncryptInputStream(inputStream: InputStream, key: String): EncryptInputStream {
        return SM4EncryptInputStream(inputStream, key)
    }
}
