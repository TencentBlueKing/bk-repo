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
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.replication.constant.BOLBS_UPLOAD_FIRST_STEP_URL_STRING
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.service.BlobChunkedService
import com.tencent.bkrepo.replication.util.BlobChunkedResponseUtils.buildBlobUploadPatchResponse
import com.tencent.bkrepo.replication.util.BlobChunkedResponseUtils.buildBlobUploadUUIDResponse
import com.tencent.bkrepo.replication.util.BlobChunkedResponseUtils.uploadResponse
import com.tencent.bkrepo.replication.util.HttpUtils.getRangeInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class BlobChunkedServiceImpl(
    private val storageService: StorageService,
): BlobChunkedService {


    @Value("\${spring.application.name}")
    private var serviceName: String = "replication"
    /**
     * 获取上传文件uuid
     */
    override fun obtainSessionIdForUpload(
        projectId: String, repoName: String, credentials: StorageCredentials, sha256: String
    ) {
        val uuidCreated = storageService.createAppendId(credentials)
        logger.info("Uuid $uuidCreated has been created for file $sha256 in repo $projectId|$repoName.")
        buildBlobUploadUUIDResponse(
            uuidCreated,
            buildLocationUrl(uuidCreated, projectId, repoName),
            HttpContextHolder.getResponse()
        )
    }

    override fun uploadChunkedFile(
        projectId: String, repoName: String,
        credentials: StorageCredentials, sha256: String, artifactFile: ArtifactFile, uuid: String
    ) {
        val range = HttpContextHolder.getRequest().getHeader("Content-Range")
        val length = HttpContextHolder.getRequest().contentLength

        val lengthOfAppendFile = storageService.findLengthOfAppendFile(uuid, credentials)
        logger.info("current length of append file is $lengthOfAppendFile")
        val (patchLen, status) = when (chunkedRequestCheck(
                    uuid = uuid,
                    lengthOfAppendFile = lengthOfAppendFile,
                    range = range,
                    contentLength = length
                )) {
            RangeStatus.ILLEGAL_RANGE -> {
                Pair(length.toLong(), HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            }
            RangeStatus.READY_TO_APPEND -> {
                val patchLen = storageService.append(
                    appendId = uuid,
                    artifactFile = artifactFile,
                    storageCredentials = credentials
                )
                logger.info(
                    "Part of file with sha256 $sha256 in repo $projectId|$repoName " +
                        "has been uploaded, size pf append file is $patchLen and uuid: $uuid"
                )
                Pair(patchLen, HttpStatus.ACCEPTED)
            }
            else -> {
                logger.info(
                    "Part of file with sha256 $sha256 in repo $projectId|$repoName " +
                        "already appended, size pf append file is $lengthOfAppendFile and uuid: $uuid")
                Pair(lengthOfAppendFile, HttpStatus.ACCEPTED)
            }
        }
        buildBlobUploadPatchResponse(
            uuid = uuid,
            locationStr = buildLocationUrl(uuid, projectId, repoName),
            response = HttpContextHolder.getResponse(),
            range = patchLen,
            status = status
        )

    }

    override fun finishChunkedUpload(
        projectId: String, repoName: String,
        credentials: StorageCredentials, sha256: String,
        artifactFile: ArtifactFile, uuid: String,
        size: Long?, md5: String?
    ) {
        storageService.append(
            appendId = uuid,
            artifactFile = artifactFile,
            storageCredentials = credentials
        )
        // 当传递了 md5和size 以后，分块文件合并时不计算 sha256 与 md5,只校验 size 是否一致
        val originalFileInfo = if (size != null && md5 != null) {
            FileInfo(sha256, md5, size)
        } else {
            null
        }
        val fileInfo = try {
            storageService.finishAppend(uuid, credentials, originalFileInfo)
        } catch (e: StorageErrorException) {
            throw BadRequestException(ReplicationMessageCode.REPLICA_ARTIFACT_BROKEN, sha256)
        }
        logger.info(
            "The file with sha256 $sha256 in repo $projectId|$repoName has been uploaded with uuid: $uuid," +
                        " received sha256 of file is ${fileInfo.sha256}")
        // 没传递 size， 校验合并的文件生成的 sha256 与传递的 sha256 是否一致
        if (size == null && fileInfo.sha256 != sha256) {
            throw BadRequestException(ReplicationMessageCode.REPLICA_ARTIFACT_BROKEN, sha256)
        }
        // 传递 size， 校验合并的文件size 与传递的 size 是否一致
        if (size != null && fileInfo.size != size) {
            throw BadRequestException(ReplicationMessageCode.REPLICA_ARTIFACT_BROKEN, sha256)
        }
        uploadResponse(
            locationStr = buildLocationUrl(uuid, projectId, repoName),
            response = HttpContextHolder.getResponse(),
            status = HttpStatus.CREATED,
        )
    }

    private fun chunkedRequestCheck(
        contentLength: Int,
        range: String?,
        uuid: String,
        lengthOfAppendFile: Long
    ): RangeStatus {
        // 当range不存在或者length < 0时
        if (!validateValue(contentLength, range)) {
            return RangeStatus.ILLEGAL_RANGE
        }
        logger.info("range $range, length $contentLength, uuid $uuid")
        val (start, end) = getRangeInfo(range!!)
        // 当上传的长度和range内容不匹配时
        return if ((end - start) != (contentLength - 1).toLong()) {
            RangeStatus.ILLEGAL_RANGE
        } else {
            // 当追加的文件大小和range的起始大小一致时代表写入正常
            if (start == lengthOfAppendFile) {
                RangeStatus.READY_TO_APPEND
            } else if (start > lengthOfAppendFile) {
                // 当追加的文件大小比start小时，说明文件写入有误
                RangeStatus.ILLEGAL_RANGE
            } else {
                // 当追加的文件大小==end+1时，可能存在重试导致已经写入一次
                if (lengthOfAppendFile == end + 1) {
                    RangeStatus.ALREADY_APPENDED
                } else {
                    // 当追加的文件大小大于start时，并且不等于end+1时，文件已损坏
                    RangeStatus.ILLEGAL_RANGE
                }
            }
        }
    }

    private fun validateValue(contentLength: Int, range: String?): Boolean {
        return !(range.isNullOrEmpty() || contentLength < 0)
    }

    private fun buildLocationUrl(uuid: String, projectId: String, repoName: String) : String {
        val path = BOLBS_UPLOAD_FIRST_STEP_URL_STRING.format(projectId, repoName)
        return serviceName+path+uuid
    }

    enum class RangeStatus {
        ILLEGAL_RANGE,
        ALREADY_APPENDED,
        READY_TO_APPEND;
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BlobChunkedServiceImpl::class.java)
    }

}
