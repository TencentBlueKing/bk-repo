/*
 *
 *  * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *  *
 *  * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *  *
 *  * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *  *
 *  * A copy of the MIT License is included in this file.
 *  *
 *  *
 *  * Terms of the MIT License:
 *  * ---------------------------------------------------
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  * the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.tencent.bkrepo.fs.server.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.model.TClient
import com.tencent.bkrepo.fs.server.pojo.ClientDetail
import com.tencent.bkrepo.fs.server.request.ClientCreateRequest
import com.tencent.bkrepo.fs.server.repository.ClientRepository
import com.tencent.bkrepo.fs.server.request.ClientListRequest
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime

class ClientService(
    private val clientRepository: ClientRepository
) {

    suspend fun createClient(request: ClientCreateRequest): ClientDetail {
        with(request) {
            val ip = ReactiveRequestContextHolder.getClientAddress()
            val query = Query(
                Criteria.where(TClient::projectId.name).isEqualTo(projectId)
                    .and(TClient::repoName.name).isEqualTo(repoName)
                    .and(TClient::mountPoint.name).isEqualTo(mountPoint)
                    .and(TClient::ip.name).isEqualTo(ip)
            )
            val client = clientRepository.findOne(query)
            return if (client == null) {
                insertClient(request)
            } else {
                updateClient(request, client)
            }.convert()
        }
    }

    suspend fun removeClient(clientId: String) {
        val query = Query(
            Criteria.where(TClient::id.name).isEqualTo(clientId)
        )
        val result = clientRepository.remove(query)
        if (result.deletedCount == 0L) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, clientId)
        }
    }

    suspend fun listClient(request: ClientListRequest): Page<ClientDetail> {
        with(request) {
            val query = Query(
                Criteria.where(TClient::projectId.name).isEqualTo(projectId)
                    .apply { repoName?.let { and(TClient::repoName.name).isEqualTo(it) } }
            )
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val clientList = clientRepository.find(query.with(pageRequest)).map { it.convert() }
            val count = clientRepository.count(query)
            return Pages.ofResponse(pageRequest, count, clientList)
        }
    }

    suspend fun heartbeat(clientId: String) {
        val query = Query(
            Criteria.where(TClient::id.name).isEqualTo(clientId)
        )
        val client = clientRepository.findOne(query)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, clientId)
        client.heartbeatTime = LocalDateTime.now()
        clientRepository.save(client)
    }

    private suspend fun insertClient(request: ClientCreateRequest): TClient {
        val client = TClient(
            projectId = request.projectId,
            repoName = request.repoName,
            mountPoint = request.mountPoint,
            userId = ReactiveSecurityUtils.getUser(),
            ip = ReactiveRequestContextHolder.getClientAddress(),
            version = request.version,
            os = request.os,
            arch = request.arch,
            online = true,
            heartbeatTime = LocalDateTime.now()
        )
        return clientRepository.save(client)
    }

    private suspend fun updateClient(request: ClientCreateRequest, client: TClient): TClient {
        val newClient = client.copy(
            userId = ReactiveSecurityUtils.getUser(),
            version = request.version,
            os = request.os,
            arch = request.arch,
            online = true,
            heartbeatTime = LocalDateTime.now()
        )
        return clientRepository.save(newClient)
    }

    private fun TClient.convert(): ClientDetail {
        return ClientDetail(
            id = id!!,
            projectId = projectId,
            repoName = repoName,
            mountPoint = mountPoint,
            userId = userId,
            ip = ip,
            version = version,
            os = os,
            arch = arch,
            online = online,
            heartbeatTime = heartbeatTime
        )
    }

}
