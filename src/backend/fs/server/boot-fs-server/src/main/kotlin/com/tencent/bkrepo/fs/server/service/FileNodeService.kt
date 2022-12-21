/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
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

package com.tencent.bkrepo.fs.server.service

import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.ZeroInputStream
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.file.FileRange
import com.tencent.bkrepo.fs.server.file.MultiArtifactFileInputStream
import com.tencent.bkrepo.fs.server.file.OverlayRangeUtils
import com.tencent.bkrepo.fs.server.storage.CoStorageManager
import java.io.InputStream
import kotlin.math.min
import kotlinx.coroutines.runBlocking

class FileNodeService(
    private val blockNodeService: BlockNodeService,
    private val coStorageManager: CoStorageManager
) {

    /**
     * 读取指定范围的文件
     * 使用块数据overlay节点数据，当节点数据不存在时，则合并块，同时块之间空的地方补0
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 节点路径
     * @param storageCredentials 仓库的存储实例
     * @param digest 节点的sha56,当节点不存在时，可以为null
     * @param size 节点的大小，当节点不存在时，可以为null
     * @param range 需要读取的文件范围
     * */
    suspend fun read(
        projectId: String,
        repoName: String,
        fullPath: String,
        storageCredentials: StorageCredentials?,
        digest: String?,
        size: Long?,
        range: Range
    ): InputStream? {
        val fileRanges = spit2FileRange(projectId, repoName, fullPath, digest, size, range)
        if (fileRanges.isEmpty()) {
            return null
        }
        return readFromFileRanges(fileRanges, storageCredentials, range.total)
    }

    /**
     * 实现文件范围的读取
     * @param fileRanges 需要读取的文件范围列表
     * @param storageCredentials 文件所在存储实例
     * @param fileLength 文件总长度
     * */
    private fun readFromFileRanges(
        fileRanges: List<FileRange>,
        storageCredentials: StorageCredentials?,
        fileLength: Long
    ): InputStream? {
        if (fileRanges.size == 1) {
            // 完整的文件数据或者块数据，直接读取返回
            return loadFileRange(fileRanges.first(), storageCredentials, fileLength)
        }
        // 复合文件、块数据
        return MultiArtifactFileInputStream(fileRanges) {
            loadFileRange(it, storageCredentials, fileLength) ?: throw ArtifactNotFoundException(it.toString())
        }
    }

    /**
     * 获取当前文件长度
     * 根据最后一个的块位置，确定文件大小
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 节点路径
     * @param size 文件大小，当文件不存在时，可以为0
     * @return 文件当前大小
     * */
    suspend fun getFileLength(projectId: String, repoName: String, fullPath: String, size: Long): Long {
        val block = blockNodeService.getLatestBlock(projectId, repoName, fullPath) ?: let {
            return size
        }
        return maxOf(block.endPos, size)
    }

    /**
     * 加载文件数据
     * @param fileRange 文件范围
     * @param storageCredentials 文件存储实例
     * @param fileLength 文件总长度
     * */
    private fun loadFileRange(
        fileRange: FileRange,
        storageCredentials: StorageCredentials?,
        fileLength: Long
    ): InputStream? {
        if (fileRange.source == FileRange.ZERO_SOURCE) {
            val len = fileRange.endPos - fileRange.startPos + 1
            return ZeroInputStream(min(len, fileLength))
        }
        val range = Range(fileRange.startPos, fileRange.endPos, fileLength)
        return runBlocking { coStorageManager.loadArtifactInputStream(fileRange.source, range, storageCredentials) }
    }

    /**
     * 根据节点数据和请求范围生成具体的请求文件范围，如果有块数据，则文件会被切分为多个数据来源
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 节点路径
     * @param sha256 节点的sha56,当节点不存在时，可以为null
     * @param size 节点的大小，当节点不存在时，可以为null
     * @param range 需要读取的文件范围
     * */
    private suspend fun spit2FileRange(
        projectId: String,
        repoName: String,
        fullPath: String,
        sha256: String?,
        size: Long?,
        range: Range
    ): List<FileRange> {
        // 找到范围内的所有分块
        val blocks = blockNodeService.listBlocks(range, projectId, repoName, fullPath)
        if (sha256 == null || size == null) {
            return OverlayRangeUtils.build(range, blocks)
        }
        return OverlayRangeUtils.build(sha256, range, size, blocks)
    }
}
