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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.opdata.config.OpProperties
import com.tencent.bkrepo.opdata.model.TPlugin
import com.tencent.bkrepo.opdata.pojo.plugin.PluginCreateRequest
import com.tencent.bkrepo.opdata.pojo.plugin.PluginInfo
import com.tencent.bkrepo.opdata.pojo.plugin.PluginListOption
import com.tencent.bkrepo.opdata.pojo.plugin.PluginUpdateRequest
import com.tencent.bkrepo.opdata.repository.PluginRepository
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class PluginService(
    private val pluginRepository: PluginRepository,
    private val opProperties: OpProperties
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30L, TimeUnit.SECONDS)
        .readTimeout(30L, TimeUnit.SECONDS)
        .writeTimeout(30L, TimeUnit.SECONDS)
        .authenticator { _, response ->
            val credential: String = Credentials.basic(opProperties.adminUsername, opProperties.adminPassword)
            response.request().newBuilder().header(HttpHeaders.AUTHORIZATION, credential).build()
        }
        .build()

    fun create(request: PluginCreateRequest) {
        with(request) {
            val userId = SecurityUtils.getUserId()
            val plugin = TPlugin(
                id = id,
                version = version,
                scope = scope,
                description = description,
                gitUrl = gitUrl,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now()
            )
            pluginRepository.insert(plugin)
        }
    }

    fun update(request: PluginUpdateRequest) {
        with(request) {
            val plugin = pluginRepository.findByIdOrNull(id)
                ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
            plugin.version = version ?: plugin.version
            plugin.scope = scope ?: plugin.scope
            plugin.description = description ?: plugin.description
            plugin.gitUrl = gitUrl ?: plugin.gitUrl
            plugin.lastModifiedBy = SecurityUtils.getUserId()
            plugin.lastModifiedDate = LocalDateTime.now()
            pluginRepository.save(plugin)
        }
    }

    fun delete(id: String) {
        val plugin = pluginRepository.findByIdOrNull(id)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        pluginRepository.deleteById(plugin.id)
    }

    fun list(option: PluginListOption): Page<PluginInfo> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val queryResult =  if (scope.isNullOrBlank()) {
                pluginRepository.findAll(pageRequest)
            } else {
                pluginRepository.findByScope(scope!!, pageRequest)
            }.map { convert(it) }
            return Pages.ofResponse(pageRequest, queryResult.totalElements, queryResult.content)
        }
    }

    fun getInfo(id: String): PluginInfo {
        val plugin = pluginRepository.findByIdOrNull(id)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        return convert(plugin)
    }

    fun load(id: String, host: String) {
        val url = "http://$host/actuator/plugin/$id"
        val requestBody = RequestBody.create(
            MediaType.parse(MediaTypes.APPLICATION_JSON),
            mapOf("id" to id).toJsonString()
        )
        val request = Request.Builder().url(url).method(HttpMethod.POST.name, requestBody).build()
        okHttpClient.newCall(request).execute().use {
            if (!it.isSuccessful) {
                throw ErrorCodeException(CommonMessageCode.SERVICE_CALL_ERROR)
            }
        }
    }

    fun unload(id: String, host: String) {
        val url = "http://$host/actuator/plugin/$id?id=$id"
        val request = Request.Builder().url(url).method(HttpMethod.DELETE.name, null).build()
        okHttpClient.newCall(request).execute().use {
            if (!it.isSuccessful) {
                throw ErrorCodeException(CommonMessageCode.SERVICE_CALL_ERROR)
            }
        }
    }


    private fun convert(tPlugin: TPlugin): PluginInfo {
        with(tPlugin) {
            return PluginInfo(
                id = id,
                version = version,
                scope = scope,
                description = description,
                gitUrl = gitUrl,
                createdBy = createdBy,
                createdDate = createdDate,
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate
            )
        }
    }
}