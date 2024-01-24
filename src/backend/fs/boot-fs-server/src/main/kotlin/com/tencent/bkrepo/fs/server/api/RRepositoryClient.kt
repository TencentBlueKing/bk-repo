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

package com.tencent.bkrepo.fs.server.api

import com.tencent.bkrepo.common.api.constant.REPOSITORY_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeLinkRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeSetLengthRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import reactivefeign.spring.config.ReactiveFeignClient
import reactor.core.publisher.Mono

@ReactiveFeignClient(REPOSITORY_SERVICE_NAME)
@RequestMapping("/service")
interface RRepositoryClient {

    @GetMapping("/node/detail/{projectId}/{repoName}")
    fun getNodeDetail(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String
    ): Mono<Response<NodeDetail?>>

    @PostMapping("/node/page/{projectId}/{repoName}")
    fun listNodePage(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam path: String,
        @RequestBody option: NodeListOption = NodeListOption()
    ): Mono<Response<Page<NodeInfo>>>

    @DeleteMapping("/node/delete")
    fun deleteNode(@RequestBody nodeDeleteRequest: NodeDeleteRequest): Mono<Response<NodeDeleteResult>>

    @PostMapping("/node/rename")
    fun renameNode(@RequestBody nodeRenameRequest: NodeRenameRequest): Mono<Response<Void>>

    @PostMapping("/node/fs/create")
    fun createNode(@RequestBody nodeCreateRequest: NodeCreateRequest): Mono<Response<NodeDetail>>

    @PutMapping("/node/fs/length")
    fun setLength(@RequestBody nodeSetLengthRequest: NodeSetLengthRequest): Mono<Response<Void>>

    @GetMapping("/node/size/{projectId}/{repoName}")
    fun computeSize(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String,
        @RequestParam estimated: Boolean = false
    ): Mono<Response<NodeSizeInfo>>

    @PostMapping("/node/link")
    fun link(@RequestBody nodeLinkRequest: NodeLinkRequest): Mono<Response<NodeDetail>>

    @GetMapping("/repo/detail/{projectId}/{repoName}")
    fun getRepoDetail(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam type: String? = null
    ): Mono<Response<RepositoryDetail?>>

    @PutMapping("/fileReference/decrement")
    fun decrement(@RequestParam sha256: String, @RequestParam credentialsKey: String?): Mono<Response<Boolean>>

    @PutMapping("/fileReference/increment")
    fun increment(@RequestParam sha256: String, @RequestParam credentialsKey: String?): Mono<Response<Boolean>>

    @PostMapping("/metadata/save")
    fun saveMetadata(@RequestBody request: MetadataSaveRequest): Mono<Response<Void>>

    @GetMapping("/metadata/list/{projectId}/{repoName}")
    fun listMetadata(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String
    ): Mono<Response<Map<String, Any>>>

    @GetMapping("/repo/stat/{projectId}/{repoName}")
    fun statRepo(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ): Mono<Response<NodeSizeInfo>>
}
