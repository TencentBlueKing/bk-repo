/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.fdtp.codec.FdtpResponseStatus
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.STORAGE_KEY
import com.tencent.bkrepo.replication.fdtp.FdtpAFTRequestHandler
import com.tencent.bkrepo.replication.fdtp.FullFdtpAFTRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

class ReplicationFdtpAFTRequestHandler(
    private val baseCacheHandler: BaseCacheHandler
): FdtpAFTRequestHandler {

    @Autowired
    lateinit var storageService: StorageService

    override fun handler(request: FullFdtpAFTRequest): FdtpResponseStatus {
        val storageKey = request.headers.get(STORAGE_KEY)
        val sha256 = request.headers.get(SHA256)!!
        logger.info("The file with sha256 [$sha256] will be handled by Fdtp!")
        val credentials = baseCacheHandler.credentialsCache.get(storageKey.orEmpty())
        if (storageService.exist(sha256, credentials)) {
            return FdtpResponseStatus.OK
        }
        if (request.artifactFile.getFileSha256() != sha256) {
            return FdtpResponseStatus(
                ArtifactMessageCode.DIGEST_CHECK_FAILED.getCode(),
                ArtifactMessageCode.DIGEST_CHECK_FAILED.name
            )
        }
        logger.info("The file with sha256 [$sha256] will be stored!")
        storageService.store(sha256, request.artifactFile, credentials)
        return FdtpResponseStatus.OK
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicationFdtpAFTRequestHandler::class.java)
    }
}