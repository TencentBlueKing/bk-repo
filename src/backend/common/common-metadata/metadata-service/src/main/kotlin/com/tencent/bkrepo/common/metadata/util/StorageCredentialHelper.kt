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
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.metadata.model.TStorageCredentials
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialsUpdater
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsCreateRequest
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsUpdateRequest
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class StorageCredentialHelper(
    credentialUpdaters: Map<String, StorageCredentialsUpdater>,
    storageProperties: StorageProperties,
) {

    init {
        Companion.credentialUpdaters = credentialUpdaters
        Companion.storageProperties = storageProperties
    }

    companion object {
        private lateinit var credentialUpdaters: Map<String, StorageCredentialsUpdater>
        private lateinit var storageProperties: StorageProperties

        fun updateCredentials(
            tStorageCredentials: TStorageCredentials,
            request: StorageCredentialsUpdateRequest
        ) {
            val storageCredentials = tStorageCredentials.credentials.readJsonString<StorageCredentials>()

            storageCredentials.apply {
                cache = cache.copy(
                    loadCacheFirst = request.credentials.cache.loadCacheFirst,
                    expireDays = request.credentials.cache.expireDays,
                    expireDuration = request.credentials.cache.expireDuration,
                    maxSize = request.credentials.cache.maxSize,
                )
                upload = upload.copy(
                    localPath = request.credentials.upload.localPath,
                    workers = request.credentials.upload.workers
                )
                compress = compress.copy(
                    path = request.credentials.compress.path,
                    ratio = request.credentials.compress.ratio
                )
                credentialUpdaters[StorageCredentialsUpdater.name(this::class.java)]?.update(this, request)
            }

            tStorageCredentials.credentials = storageCredentials.toJsonString()
        }

        fun convert(credentials: TStorageCredentials): StorageCredentials {
            return credentials.credentials.readJsonString<StorageCredentials>().apply { this.key = credentials.id }
        }

        fun buildStorageCredential(
            request: StorageCredentialsCreateRequest,
            userId: String
        ) = TStorageCredentials(
            id = request.key,
            createdBy = userId,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = userId,
            lastModifiedDate = LocalDateTime.now(),
            credentials = request.credentials.toJsonString(),
            region = request.region
        )

        fun checkCreateRequest(request: StorageCredentialsCreateRequest) {
            takeIf { request.key.isNotBlank() } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "key")
            // 目前的实现方式有个限制：新增的存储方式和默认的存储方式必须相同
            if (storageProperties.defaultStorageCredentials()::class != request.credentials::class) {
                throw throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "type")
            }
        }
    }
}
