/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.controller.api

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fdtp.codec.FdtpResponseStatus
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.STORAGE_KEY
import com.tencent.bkrepo.replication.fdtp.FdtpAFTRequestHandler
import com.tencent.bkrepo.replication.fdtp.FullFdtpAFTRequest
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.TimeUnit

class ReplicationFdtpAFTRequestHandler(
    private val storageProperties: StorageProperties
): FdtpAFTRequestHandler {

    @Autowired
    lateinit var storageService: StorageService
    @Autowired
    lateinit var storageCredentialsClient: StorageCredentialsClient

    // TODO 重复代码处理
    private val defaultCredentials = storageProperties.defaultStorageCredentials()
    private val credentialsCache: LoadingCache<String, StorageCredentials> = CacheBuilder.newBuilder()
        .maximumSize(10L)
        .expireAfterWrite(5L, TimeUnit.MINUTES)
        .build(CacheLoader.from { key -> findStorageCredentials(key) })

    override fun handler(request: FullFdtpAFTRequest): FdtpResponseStatus {
        val storageKey = request.headers.get(STORAGE_KEY)
        val sha256 = request.headers.get(SHA256)!!
        logger.info("The file with sha256 [$sha256] will be handled by Fdtp!")
        val credentials = credentialsCache.get(storageKey.orEmpty())
        if (storageService.exist(sha256, credentials)) {
            return FdtpResponseStatus.OK
        }
        if (request.artifactFile.getFileSha256() != sha256) {
            // TODO 错误返回需要确定
            return FdtpResponseStatus(ArtifactMessageCode.DIGEST_CHECK_FAILED.getCode(), "sha256")
        }
        logger.info("The file with sha256 [$sha256] will be stored!")
        storageService.store(sha256, request.artifactFile, credentials)
        return FdtpResponseStatus.OK
    }

    private fun findStorageCredentials(storageKey: String?): StorageCredentials {
        if (storageKey.isNullOrBlank()) {
            return defaultCredentials
        }
        return storageCredentialsClient.findByKey(storageKey).data ?: defaultCredentials
    }
    companion object {
        private val logger = LoggerFactory.getLogger(ReplicationFdtpAFTRequestHandler::class.java)
    }
}