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

package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.replication.constant.BOLBS_UPLOAD_FIRST_STEP_URL
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.service.BlobChunkedService
import com.tencent.bkrepo.replication.util.BlobChunkedResponseUtils.buildBlobUploadPatchResponse
import com.tencent.bkrepo.replication.util.BlobChunkedResponseUtils.buildBlobUploadUUIDResponse
import com.tencent.bkrepo.replication.util.BlobChunkedResponseUtils.uploadResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class BlobChunkedServiceImpl(
    private val storageService: StorageService,
): BlobChunkedService {

    /**
     * 获取上传文件uuid
     */
    override fun obtainSessionIdForUpload(credentials: StorageCredentials, sha256: String) {
        val uuidCreated = storageService.createAppendId(credentials)
        logger.info("Uuid $uuidCreated has been created for File $sha256.")
        buildBlobUploadUUIDResponse(
            uuidCreated,
            BOLBS_UPLOAD_FIRST_STEP_URL+uuidCreated,
            HttpContextHolder.getResponse()
        )
    }

    override fun uploadChunkedFile(
        credentials: StorageCredentials, sha256: String, file: MultipartFile, uuid: String
    ) {
        val range = HttpContextHolder.getRequest().getHeader("Content-Range")
        val length = HttpContextHolder.getRequest().contentLength
        if (!range.isNullOrEmpty() && length > -1) {
            logger.info("range $range, length $length, uuid $uuid")
            val (start, end) = getRangeInfo(range)
            // 判断要上传的长度是否超长
            if (end - start > length - 1) {
                return
            }
        }
        val artifactFile = ArtifactFileFactory.build(file, credentials)
        val patchLen = storageService.append(
            appendId = uuid,
            artifactFile = artifactFile,
            storageCredentials = credentials
        )

        buildBlobUploadPatchResponse(
            uuid = uuid,
            locationStr =  BOLBS_UPLOAD_FIRST_STEP_URL+uuid,
            response = HttpContextHolder.getResponse(),
            range = patchLen
        )
    }

    override fun finishChunkedUpload(
        credentials: StorageCredentials, sha256: String, file: MultipartFile, uuid: String
    ) {
        val artifactFile = ArtifactFileFactory.build(file, credentials)

        storageService.append(
            appendId = uuid,
            artifactFile = artifactFile,
            storageCredentials = credentials
        )
        val fileInfo = storageService.finishAppend(uuid, credentials)
        if (fileInfo.sha256 != sha256) {
            throw BadRequestException(ReplicationMessageCode.REPLICA_ARTIFACT_BROKEN, sha256)
        }
        uploadResponse(
            locationStr = BOLBS_UPLOAD_FIRST_STEP_URL+uuid,
            response = HttpContextHolder.getResponse(),
            status = HttpStatus.OK,
        )
    }

    /**
     * 从Content-Range头中解析出起始位置
     */
    private fun getRangeInfo(range: String): Pair<Long, Long> {
        val values = range.split("-")
        return Pair(values[0].toLong(), values[1].toLong())
    }


    companion object {
        private val logger = LoggerFactory.getLogger(BlobChunkedServiceImpl::class.java)
    }

}
