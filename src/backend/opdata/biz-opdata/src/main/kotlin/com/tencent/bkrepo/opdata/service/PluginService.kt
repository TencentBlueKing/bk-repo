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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.opdata.client.plugin.PluginClient
import com.tencent.bkrepo.opdata.model.TPlugin
import com.tencent.bkrepo.opdata.pojo.plugin.PluginCreateRequest
import com.tencent.bkrepo.opdata.pojo.plugin.PluginDetail
import com.tencent.bkrepo.opdata.pojo.plugin.PluginListOption
import com.tencent.bkrepo.opdata.pojo.plugin.PluginUpdateRequest
import com.tencent.bkrepo.opdata.repository.PluginRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PluginService(
    private val pluginRepository: PluginRepository,
    private val pluginClient: PluginClient
) {

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
            plugin.scope = if (scope.isNullOrEmpty()) plugin.scope else scope!!
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

    fun list(option: PluginListOption): Page<PluginDetail> {
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

    fun load(id: String, host: String) {
        pluginClient.load(id, host)
    }

    fun unload(id: String, host: String) {
        pluginClient.unload(id, host)
    }

    private fun convert(tPlugin: TPlugin): PluginDetail {
        with(tPlugin) {
            return PluginDetail(
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
