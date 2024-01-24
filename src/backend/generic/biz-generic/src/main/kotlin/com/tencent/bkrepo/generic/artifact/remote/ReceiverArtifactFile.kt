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

package com.tencent.bkrepo.generic.artifact.remote

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactDataReceiver
import java.io.File
import java.io.InputStream

/**
 * 通过已完成文件接收的[ArtifactDataReceiver]构造的[ArtifactFile]
 */
class ReceiverArtifactFile(
    /**
     * 文件接收器，必须为接收完成状态
     */
    private val receiver: ArtifactDataReceiver,

    /**
     * receiver接收文件的路径是否在本地磁盘
     */
    private val inLocalDisk: Boolean,
) : ArtifactFile {

    init {
        require(receiver.finished)
    }

    /**
     * 文件sha1值
     */
    private var sha1: String? = null

    override fun getInputStream(): InputStream {
        return receiver.getInputStream()
    }

    override fun getSize(): Long {
        return receiver.received
    }

    override fun isInMemory(): Boolean {
        return receiver.inMemory
    }

    override fun getFile(): File? {
        return if (!isInMemory()) {
            receiver.filePath.toFile()
        } else {
            null
        }
    }

    override fun flushToFile(): File {
        receiver.flushToFile()
        return receiver.filePath.toFile()
    }

    override fun isFallback(): Boolean {
        return receiver.fallback
    }

    override fun getFileMd5(): String {
        return receiver.listener.getMd5()
    }

    /**
     * sha1的计算会重新读取流
     */
    override fun getFileSha1(): String {
        return sha1 ?: getInputStream().sha1().apply { sha1 = this }
    }

    override fun getFileSha256(): String {
        return receiver.listener.getSha256()
    }

    override fun delete() {
        receiver.close()
    }

    override fun hasInitialized(): Boolean {
        return true
    }

    override fun isInLocalDisk() = inLocalDisk
}
