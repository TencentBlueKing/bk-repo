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

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.fs.server.request.ClientCreateRequest
import com.tencent.bkrepo.fs.server.pojo.ClientListRequest
import com.tencent.bkrepo.fs.server.service.ClientService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.queryParamOrNull

class ClientHandler(
    private val clientService: ClientService
) {

    suspend fun createClient(request: ServerRequest): ServerResponse {
        val createRequest = request.bodyToMono(ClientCreateRequest::class.java).awaitSingle()
        val clientDetail = clientService.createClient(createRequest)
        return ReactiveResponseBuilder.success(clientDetail)
    }

    suspend fun removeClient(request: ServerRequest): ServerResponse {
        val projectId = request.pathVariable("projectId")
        val repoName = request.pathVariable("repoName")
        val clientId = request.pathVariable("clientId")
        return ReactiveResponseBuilder.success(clientService.removeClient(projectId, repoName, clientId))
    }

    suspend fun heartbeat(request: ServerRequest): ServerResponse {
        val projectId = request.pathVariable("projectId")
        val repoName = request.pathVariable("repoName")
        val clientId = request.pathVariable("clientId")
        clientService.heartbeat(projectId, repoName, clientId)
        return ReactiveResponseBuilder.success()
    }

    suspend fun listClients(request: ServerRequest): ServerResponse {
        val listRequest = ClientListRequest(
            projectId = request.queryParamOrNull(ClientListRequest::projectId.name),
            repoName = request.queryParamOrNull(ClientListRequest::repoName.name),
            pageNumber = request.queryParamOrNull(ClientListRequest::pageNumber.name)?.toInt() ?: DEFAULT_PAGE_NUMBER,
            pageSize = request.queryParamOrNull(ClientListRequest::pageSize.name)?.toInt() ?: DEFAULT_PAGE_SIZE,
            online = request.queryParamOrNull(ClientListRequest::online.name)?.toBoolean(),
            ip = request.queryParamOrNull(ClientListRequest::ip.name),
            version = request.queryParamOrNull(ClientListRequest::version.name)
        )
        return ReactiveResponseBuilder.success(clientService.listClients(listRequest))
    }
}