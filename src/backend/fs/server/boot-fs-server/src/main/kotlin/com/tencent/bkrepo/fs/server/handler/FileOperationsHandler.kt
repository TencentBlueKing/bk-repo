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

import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.fs.server.RepositoryCache
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.io.RegionInputStreamResource
import com.tencent.bkrepo.fs.server.request.NodeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.core.io.FileSystemResource
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

/**
 * 文件操作相关处理器
 *
 * 处理文件操作请求
 * */
class FileOperationsHandler(
    private val storageService: StorageService,
    private val rRepositoryClient: RRepositoryClient
) {

    suspend fun download(request: ServerRequest): ServerResponse {
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
            val artifactInputStream = withContext(Dispatchers.IO) {
                storageService.load(node.sha256!!, range, repo.storageCredentials)
            } ?: return ServerResponse.notFound().buildAndAwait()
            val source = if (artifactInputStream is FileArtifactInputStream) {
                FileSystemResource(artifactInputStream.file)
            } else {
                RegionInputStreamResource(artifactInputStream, range.length)
            }
            return ok().bodyValueAndAwait(source)
        }
    }
}
