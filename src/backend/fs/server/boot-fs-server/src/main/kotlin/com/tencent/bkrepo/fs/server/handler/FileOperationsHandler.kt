/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.handler

import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.RepositoryCache
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.file.ReactiveArtifactFile
import com.tencent.bkrepo.fs.server.io.RegionInputStreamResource
import com.tencent.bkrepo.fs.server.listener.NodeFlushEvent
import com.tencent.bkrepo.fs.server.model.TBlockNode
import com.tencent.bkrepo.fs.server.repository.BlockNodeRepository
import com.tencent.bkrepo.fs.server.request.NodeRequest
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationContext
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.bodyToFlow
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

/**
 * 文件操作相关处理器
 *
 * 处理文件操作请求
 * */
class FileOperationsHandler(
    private val storageService: StorageService,
    private val rRepositoryClient: RRepositoryClient,
    private val blockNodeRepository: BlockNodeRepository,
    private val storageProperties: StorageProperties,
    private val applicationContext: ApplicationContext
) {

    suspend fun download(request: ServerRequest): ServerResponse {
        println("enter file operation")
        with(NodeRequest(request)) {
            val repo = RepositoryCache.getRepoDetail(projectId, repoName)
            val node = rRepositoryClient.getNodeDetail(projectId, repoName, fullPath).awaitSingle().data!!
            val length = node.size
            val httpRange = request.headers().range().firstOrNull()?.let {
                val startPosition = it.getRangeStart(length)
                val endPosition = it.getRangeEnd(length)
                Range(startPosition, endPosition, length)
            }
            val range = httpRange ?: Range.full(length)
            val artifactInputStream = storageService.load(node.sha256!!, range, repo.storageCredentials)
                ?: return ServerResponse.notFound().buildAndAwait()
            val source = if (artifactInputStream is FileArtifactInputStream) {
                FileSystemResource(artifactInputStream.file)
            } else {
                RegionInputStreamResource(artifactInputStream, range.length)
            }
            return ok().bodyValueAndAwait(source)
        }
    }

    suspend fun readBlock(request: ServerRequest): ServerResponse {
      /*
      * 1. 获取待读取的的block index和Node信息
      * 2. 查询出待读取的block node
      * 3. 读取block data
      * */
        with(NodeRequest(request)) {
            val repo = RepositoryCache.getRepoDetail(projectId, repoName)
            val index = request.pathVariable("index").toLong()
            val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
                .and(TBlockNode::index.name).isEqualTo(index)
                .and(TBlockNode::effective.name).isEqualTo(true)
            // 读取最新版本
            val query = Query(criteria)
            query.with(Sort.by(Sort.Direction.DESC, TBlockNode::version.name))
            val blockNode = blockNodeRepository.findOne(query) ?: throw RuntimeException()
            val range = Range.full(blockNode.size.toLong())
            val artifactInputStream = storageService.load(blockNode.sha256, range, repo.storageCredentials)
                ?: return ServerResponse.notFound().buildAndAwait()
            val source = if (artifactInputStream is FileArtifactInputStream) {
                FileSystemResource(artifactInputStream.file)
            } else {
                RegionInputStreamResource(artifactInputStream, range.length)
            }
            return ok().bodyValueAndAwait(source)
        }
    }

    suspend fun writeBlock(request: ServerRequest): ServerResponse {
        /*
        * 1. 获取待写入的block index
        * 2. 获取带入写文件的Node信息
        * 3. 写入新block数据
        * 4. 更新block node
        * 5. 删除旧的block data
        * */
        with(NodeRequest(request)) {
            val node = rRepositoryClient.getNodeDetail(projectId, repoName, fullPath).awaitSingle().data!!
            if (node.folder) {
                throw RuntimeException("没找到文件节点")
            }
            // todo 覆盖上传文件需要删除所有分块
            val repo = RepositoryCache.getRepoDetail(projectId, repoName)
            val user = request.attributes()[USER_KEY] as String
            val index = request.pathVariable("index").toLong()
            val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
                .and(TBlockNode::index.name).isEqualTo(index)
            val query = Query(criteria)
            query.with(Sort.by(Sort.Direction.DESC, TBlockNode::version.name))
            val oldVersion = blockNodeRepository.findOne(query)?.version ?: 0
            val version = oldVersion + 1
            val storageCredentials = repo.storageCredentials ?: storageProperties.defaultStorageCredentials()
            val artifactFile = receive(request, storageCredentials)
            // 找到当前最新版本
            val blockNode = TBlockNode(
                createdBy = user,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = user,
                lastModifiedDate = LocalDateTime.now(),
                nodeFullPath = fullPath,
                index = index,
                sha256 = artifactFile.getFileSha256(),
                projectId = projectId,
                repoName = repoName,
                effective = false,
                size = artifactFile.getSize().toInt(),
                version = version,
                isDeleted = false
            )
//            store(artifactFile, storageCredentials)
            blockNodeRepository.save(blockNode)
            return ok().bodyValueAndAwait(blockNode)
        }
    }

    suspend fun complete(request: ServerRequest): ServerResponse {
        // 使block node生效
        with(NodeRequest(request)) {
            val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
                .and(TBlockNode::effective.name).isEqualTo(false)
            val query = Query(criteria)
            val update = Update().set(TBlockNode::effective.name, true)
            val ret = blockNodeRepository.updateMulti(query, update)
            val repo = RepositoryCache.getRepoDetail(projectId, repoName)
            val storageCredentials = repo.storageCredentials ?: storageProperties.defaultStorageCredentials()
            val flushEvent = NodeFlushEvent(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                storageCredentials = storageCredentials,
                userId = request.attributes()[USER_KEY] as String
            )
            applicationContext.publishEvent(flushEvent)
            return ok().bodyValueAndAwait(ret)
        }
    }

    private suspend fun store(file: ArtifactFile, storageCredentials: StorageCredentials?) {
        withContext(Dispatchers.IO) {
            val digest = file.getFileSha256()
            storageService.store(digest, file, storageCredentials)
        }
    }

    private suspend fun receive(request: ServerRequest, storageCredentials: StorageCredentials): ReactiveArtifactFile {
        val reactiveArtifactFile = ReactiveArtifactFile(storageCredentials)
        request.bodyToFlow<DataBuffer>().onCompletion {
            reactiveArtifactFile.finish()
        }.collect {
            reactiveArtifactFile.write(it)
        }
        reactiveArtifactFile.delete()
        return reactiveArtifactFile
    }
}
