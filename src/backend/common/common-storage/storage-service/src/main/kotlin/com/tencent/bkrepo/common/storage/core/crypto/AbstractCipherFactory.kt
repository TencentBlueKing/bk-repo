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
import java.io.InputStream
import java.io.OutputStream

/**
 * 抽象加密工厂
 * */
abstract class AbstractCipherFactory {
    /**
     * 获取加密的输出流，将写入的数据加密后，写入到[outputStream]
     *
     * @param outputStream 输出
     * @param key 密钥
     * @return 密文输出流
     * */
    abstract fun getEncryptOutputStream(outputStream: OutputStream, key: String): CipherOutputStream

    /**
     * 获取一个明文输入流，读取[inputStream]中的加密数据，并将其解密，转换成新的输入流
     *
     * @param inputStream 密文输入流
     * @param key 密钥
     * @return 明文输入流
     * */
    abstract fun getPlainInputStream(inputStream: InputStream, key: String): CipherInputStream

    /**
     * 获取一个加密的输入流，读取[inputStream]中的数据，并将其加密,转换成新的输入流
     *
     * @param inputStream 明文输入流
     * @param key 密钥
     * @return 密文输入流
     * */
    abstract fun getEncryptInputStream(inputStream: InputStream, key: String): EncryptInputStream
}
