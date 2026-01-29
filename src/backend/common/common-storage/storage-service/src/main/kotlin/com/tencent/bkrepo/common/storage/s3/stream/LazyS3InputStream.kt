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

package com.tencent.bkrepo.common.storage.s3.stream

import com.amazonaws.services.s3.model.GetObjectRequest
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.common.storage.s3.S3Client
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * 延时请求的S3输入流
 * S3输入流一段时间内未读取可能会被关闭，延时请求以避免下载目录或批量下载时读取失败导致压缩包中缺失部分文件
 */
class LazyS3InputStream(
    private val client: S3Client,
    private val request: GetObjectRequest
) : InputStream() {

    private val s3InputStream by lazy {
        client.s3Client.getObject(request).objectContent ?: run {
            logger.error("Error getting S3 object [${request.key}] during LazyS3InputStream initialization")
            throw StorageErrorException(StorageMessageCode.LOAD_ERROR)
        }
    }

    override fun read() = s3InputStream.read()
    override fun read(byteArray: ByteArray) = s3InputStream.read(byteArray)
    override fun read(byteArray: ByteArray, off: Int, len: Int) = s3InputStream.read(byteArray, off, len)
    override fun close() = s3InputStream.close()
    override fun skip(n: Long) = s3InputStream.skip(n)
    override fun available() = s3InputStream.available()
    override fun reset() = s3InputStream.reset()
    override fun mark(readlimit: Int) = s3InputStream.mark(readlimit)
    override fun markSupported() = s3InputStream.markSupported()

    companion object {
        private val logger = LoggerFactory.getLogger(LazyS3InputStream::class.java)
    }
}
