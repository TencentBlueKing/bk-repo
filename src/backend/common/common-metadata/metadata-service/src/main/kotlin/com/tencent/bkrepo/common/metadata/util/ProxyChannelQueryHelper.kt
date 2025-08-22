/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.metadata.model.TProxyChannel
import com.tencent.bkrepo.common.security.util.RsaUtils
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelCreateRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

object ProxyChannelQueryHelper {

    fun buildSingleQuery(
        projectId: String,
        repoName: String,
        repoType: String,
        name: String? = null
    ): Query {
        val criteria = where(TProxyChannel::projectId).isEqualTo(projectId)
            .and(TProxyChannel::repoName).isEqualTo(repoName)
            .and(TProxyChannel::repoType).isEqualTo(repoType)
            .apply {
                name?.let {
                    this.and(TProxyChannel::name).isEqualTo(name)
                }
            }
        return Query(criteria)
    }

    private fun formatUrl(url: String): String {
        return try {
            UrlFormatter.formatUrl(url)
        } catch (exception: IllegalArgumentException) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "url")
        }
    }

    fun encryptPassword(password: String?): String? {
        val pw = if (password.isNullOrEmpty()) {
            password
        } else {
            RsaUtils.encrypt(password)
        }
        return pw
    }

    fun ProxyChannelCreateRequest.convertToTProxyChannel(
        userId: String
    ): TProxyChannel {
        val pw = encryptPassword(password)
        val tProxyChannel = TProxyChannel(
            public = public,
            name = name.trim(),
            url = formatUrl(url),
            repoType = repoType,
            credentialKey = credentialKey,
            username = username,
            password = pw,
            projectId = projectId,
            repoName = repoName,
            createdBy = userId,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = userId,
            lastModifiedDate = LocalDateTime.now()
        )
        return tProxyChannel
    }

    fun convert(tProxyChannel: TProxyChannel?): ProxyChannelInfo? {
        return tProxyChannel?.let {
            val pw = if (it.password.isNullOrEmpty()) {
                it.password
            } else {
                try {
                    RsaUtils.decrypt(it.password!!)
                } catch (e: Exception) {
                    it.password
                }
            }
            ProxyChannelInfo(
                id = it.id!!,
                public = it.public,
                name = it.name,
                url = it.url,
                repoType = it.repoType,
                credentialKey = it.credentialKey,
                username = it.username,
                password = pw,
                projectId = it.projectId,
                repoName = it.repoName,
                lastSyncStatus = it.lastSyncStatus,
                lastSyncDate = it.lastSyncDate
            )
        }
    }
}
