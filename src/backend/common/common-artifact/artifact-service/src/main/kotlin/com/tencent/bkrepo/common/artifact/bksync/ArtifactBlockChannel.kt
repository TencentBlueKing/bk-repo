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

package com.tencent.bkrepo.common.artifact.bksync

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.bksync.BlockChannel
import com.tencent.bkrepo.common.bksync.ByteArrayBlockChannel
import com.tencent.bkrepo.common.bksync.FileBlockChannel
import java.io.ByteArrayOutputStream
import java.nio.channels.WritableByteChannel

/**
 * 构件块输入流
 * 可以实现延迟加载文件
 * */
class ArtifactBlockChannel(val artifactFile: ArtifactFile, val name: String) : BlockChannel {

    private lateinit var blockChannel: BlockChannel
    private var initial = false

    override fun transferTo(startSeq: Int, endSeq: Int, blockSize: Int, target: WritableByteChannel): Long {
        init()
        return blockChannel.transferTo(startSeq, endSeq, blockSize, target)
    }

    override fun totalSize(): Long {
        init()
        return blockChannel.totalSize()
    }

    override fun name(): String {
        return blockChannel.name()
    }

    override fun close() {
        if (initial) {
            blockChannel.close()
        }
    }

    private fun init() {
        if (initial) {
            return
        }
        blockChannel = if (artifactFile.isInMemory()) {
            val dataOutput = ByteArrayOutputStream()
            artifactFile.getInputStream().copyTo(dataOutput)
            ByteArrayBlockChannel(dataOutput.toByteArray(), name)
        } else {
            val file = artifactFile.getFile()!!
            FileBlockChannel(file, name)
        }
        initial = true
    }
}
