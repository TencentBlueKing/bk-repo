/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.innercos.stream

import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * 延时请求的COS输入流
 * COS输入流1分钟内未读取会被关闭，延时请求以避免下载目录或批量下载时读取失败导致压缩包中缺失部分文件
 */
class LazyCosInputStream(
    private val client: CosClient,
    private val request: GetObjectRequest
) : InputStream() {

    private val cosInputStream by lazy {
        client.getObjectByChunked(request).inputStream ?: run {
            logger.error("Error getting cos object [${request.key}] during LazyCosInputStream initialization")
            throw StorageErrorException(StorageMessageCode.LOAD_ERROR)
        }
    }

    override fun read() = cosInputStream.read()
    override fun read(byteArray: ByteArray) = cosInputStream.read(byteArray)
    override fun read(byteArray: ByteArray, off: Int, len: Int) = cosInputStream.read(byteArray, off, len)
    override fun close() = cosInputStream.close()
    override fun skip(n: Long) = cosInputStream.skip(n)
    override fun available() = cosInputStream.available()
    override fun reset() = cosInputStream.reset()
    override fun mark(readlimit: Int) = cosInputStream.mark(readlimit)
    override fun markSupported() = cosInputStream.markSupported()

    companion object {
        private val logger = LoggerFactory.getLogger(LazyCosInputStream::class.java)
    }
}
