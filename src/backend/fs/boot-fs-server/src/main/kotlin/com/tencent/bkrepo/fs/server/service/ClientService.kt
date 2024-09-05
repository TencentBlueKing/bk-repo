/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.fs.server.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.metrics.push.custom.CustomMetricsExporter
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.metrics.push.custom.enums.DataModel
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.model.TClient
import com.tencent.bkrepo.fs.server.model.TDailyClient
import com.tencent.bkrepo.fs.server.pojo.ClientDetail
import com.tencent.bkrepo.fs.server.pojo.ClientListRequest
import com.tencent.bkrepo.fs.server.pojo.DailyClientDetail
import com.tencent.bkrepo.fs.server.pojo.DailyClientListRequest
import com.tencent.bkrepo.fs.server.repository.ClientRepository
import com.tencent.bkrepo.fs.server.repository.DailyClientRepository
import com.tencent.bkrepo.fs.server.request.ClientCreateRequest
import com.tencent.bkrepo.fs.server.request.ClientPushMetricsRequest
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ClientService(
    private val clientRepository: ClientRepository,
    private val dailyClientRepository: DailyClientRepository,
    private val customMetricsExporter: CustomMetricsExporter? = null
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
            recordDairyClient(request, "start")
            return if (client == null) {
                insertClient(request)
            } else {
                updateClient(request, client)
            }.convert()
        }
    }

    suspend fun removeClient(projectId: String, repoName: String, clientId: String) {
        val query = Query(
            Criteria.where(TClient::projectId.name).isEqualTo(projectId)
                .and(TClient::repoName.name).isEqualTo(repoName)
                .and(TClient::id.name).isEqualTo(clientId)
        )
        val client = clientRepository.findOne(query)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, clientId)
        val request = convertClientRequest(client)
        recordDairyClient(request, "finish")
        clientRepository.remove(query)
    }

    suspend fun heartbeat(projectId: String, repoName: String, clientId: String) {
        val query = Query(
            Criteria.where(TClient::projectId.name).isEqualTo(projectId)
                .and(TClient::repoName.name).isEqualTo(repoName)
                .and(TClient::id.name).isEqualTo(clientId)
        )
        val client = clientRepository.findOne(query)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, clientId)
        client.heartbeatTime = LocalDateTime.now()
        client.online = true
        clientRepository.save(client)
        recordDairyClient(client)
    }

    suspend fun listClients(request: ClientListRequest): Page<ClientDetail> {
        val pageRequest = Pages.ofRequest(request.pageNumber, request.pageSize)
        val criteria = Criteria()
        request.projectId?.let { criteria.and(TClient::projectId.name).isEqualTo(it) }
        request.repoName?.let { criteria.and(TClient::repoName.name).isEqualTo(it) }
        request.online?.let { criteria.and(TClient::online.name).isEqualTo(it) }
        request.ip?.let { criteria.and(TClient::ip.name).regex(convertToRegex(it)) }
        request.version?.let { criteria.and(TClient::version.name).regex(convertToRegex(it)) }
        request.userId?.let { criteria.and(TClient::userId.name).isEqualTo(it) }
        val query = Query(criteria)
        val count = clientRepository.count(query)
        val data = clientRepository.find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, count, data.map { it.convert() })
    }

    suspend fun pushMetrics(request: ClientPushMetricsRequest) {
        with(request) {
            val ip = ReactiveRequestContextHolder.getClientAddress()
            metrics.forEach {
                val newLabels = mutableMapOf<String, String>()
                newLabels.putAll(it.labels)
                newLabels["clientIp"] = ip
                val metricItem = try {
                    MetricsItem(
                        it.metricName, it.metricHelp, DataModel.valueOf(it.metricDataModel),
                        it.keepHistory, it.value.toDouble(), newLabels
                    )
                } catch (e: Exception) {
                    throw ErrorCodeException(CommonMessageCode.REQUEST_CONTENT_INVALID, it)
                }
                customMetricsExporter?.reportMetrics(metricItem)
            }
        }
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

    private suspend fun recordDairyClient(request: ClientCreateRequest, action: String) {
        insertDairyClient(request, action)
    }

    private suspend fun recordDairyClient(client: TClient) {
        val query = Query(
            Criteria.where(TDailyClient::projectId.name).isEqualTo(client.projectId)
                .and(TDailyClient::repoName.name).isEqualTo(client.repoName)
                .and(TDailyClient::ip.name).isEqualTo(client.ip)
                .and(TDailyClient::action.name).isEqualTo("working")
                .and(TDailyClient::mountPoint.name).isEqualTo(client.mountPoint)
                .and(TDailyClient::time.name).gt(LocalDate.now().atStartOfDay())
                .lt(LocalDate.now().atStartOfDay().plusDays(1))
        )
        val dailyClient = dailyClientRepository.findOne(query)
        val request = convertClientRequest(client)
        if (dailyClient == null) {
            insertDairyClient(request, "working")
        } else {
            updateDairyClient(dailyClient)
        }
    }

    private suspend fun insertDairyClient(request: ClientCreateRequest, action: String): TDailyClient {
        val client = TDailyClient(
            projectId = request.projectId,
            repoName = request.repoName,
            mountPoint = request.mountPoint,
            userId = ReactiveSecurityUtils.getUser(),
            ip = ReactiveRequestContextHolder.getClientAddress(),
            version = request.version,
            os = request.os,
            arch = request.arch,
            action = action,
            time = LocalDateTime.now()
        )
        return dailyClientRepository.save(client)
    }

    private suspend fun updateDairyClient(client: TDailyClient): TDailyClient {
        val newClient = client.copy(
            time = LocalDateTime.now()
        )
        return dailyClientRepository.save(newClient)
    }

    suspend fun listDailyClients(request: DailyClientListRequest): Page<DailyClientDetail> {
        val pageRequest = Pages.ofRequest(request.pageNumber, request.pageSize)
        val criteria = Criteria()
        request.projectId?.let { criteria.and(TDailyClient::projectId.name).isEqualTo(request.projectId) }
        request.repoName?.let { criteria.and(TDailyClient::repoName.name).isEqualTo(request.repoName) }
        request.ip?.let { criteria.and(TDailyClient::ip.name).isEqualTo(request.ip) }
        request.version?.let { criteria.and(TDailyClient::version.name).isEqualTo(request.version) }
        var endTime = LocalDateTime.now()
        request.endTime?.let {
            val target = LocalDate.parse(
                request.endTime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
            ).atStartOfDay().plusDays(1)
            endTime = target
        }
        if (!request.startTime.isNullOrEmpty()) {
            val target = LocalDate.parse(
                request.startTime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
            ).atStartOfDay()
            criteria.and(TDailyClient::time.name).lt(endTime).gte(target)
        } else {
            criteria.and(TDailyClient::time.name).lt(endTime)
        }
        request.mountPoint?.let {
            criteria.and(TDailyClient::mountPoint.name).isEqualTo(request.mountPoint)
        }
        criteria.and(TDailyClient::action.name).`in`(request.actions)
        val query = Query(criteria).with(Sort.by(Sort.Direction.DESC, TDailyClient::time.name))
        val count = dailyClientRepository.count(query)
        val data = dailyClientRepository.find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, count, data.map { it.convert() })
    }

    private fun convertClientRequest(client: TClient): ClientCreateRequest {
        return ClientCreateRequest(
            projectId = client.projectId,
            repoName = client.repoName,
            mountPoint = client.mountPoint,
            version = client.version,
            os = client.os,
            arch = client.arch
        )
    }

    private fun convertToRegex(value: String): String {
        return EscapeUtils.escapeRegexExceptWildcard(value).replace("*", ".*")
    }

    private fun TDailyClient.convert(): DailyClientDetail {
        return DailyClientDetail(
            id = id!!,
            projectId = projectId,
            repoName = repoName,
            mountPoint = mountPoint,
            userId = userId,
            ip = ip,
            version = version,
            os = os,
            arch = arch,
            action = action,
            time = time
        )
    }
}
