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

package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.cns.CnsService
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.replication.constant.BLOB_CHECK_URI
import com.tencent.bkrepo.replication.constant.BLOB_PULL_URI
import com.tencent.bkrepo.replication.constant.BLOB_PUSH_URI
import com.tencent.bkrepo.replication.constant.BOLBS_UPLOAD_FIRST_STEP_URL
import com.tencent.bkrepo.replication.constant.BOLBS_UPLOAD_SECOND_STEP_URL
import com.tencent.bkrepo.replication.pojo.blob.BlobPullRequest
import com.tencent.bkrepo.replication.service.BlobChunkedService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * blob数据同步接口
 * 用于同个集群中不同节点之间blob数据同步
 */
@Principal(type = PrincipalType.ADMIN)
@RestController
class BlobReplicaController(
    private val storageService: StorageService,
    private val blobChunkedService: BlobChunkedService,
    private val baseCacheHandler: BaseCacheHandler
) {

    @Autowired(required = false)
    private var cnsService: CnsService? = null

    @PostMapping(BLOB_PULL_URI)
    fun pull(@RequestBody request: BlobPullRequest): ResponseEntity<InputStreamResource> {
        with(request) {
            val credentials = baseCacheHandler.credentialsCache.get(storageKey.orEmpty())
            val inputStream = storageService.load(sha256, range, credentials)
                ?: throw NotFoundException(ArtifactMessageCode.ARTIFACT_DATA_NOT_FOUND)
            return ResponseEntity.ok(InputStreamResource(inputStream))
        }
    }

    @PostMapping(BLOB_PUSH_URI)
    fun push(
        @RequestPart file: MultipartFile,
        @RequestParam sha256: String,
        @RequestParam size: String? = null,
        @RequestParam storageKey: String? = null
    ): Response<Void> {
        logger.info("The file with sha256 [$sha256] will be handled!")
        val credentials = baseCacheHandler.credentialsCache.get(storageKey.orEmpty())
        if (storageService.exist(sha256, credentials)) {
            return ResponseBuilder.success()
        }
        if (!size.isNullOrEmpty() && size.toLong() != file.size) {
            throw ErrorCodeException(ArtifactMessageCode.SIZE_CHECK_FAILED, file.size)
        }
        val artifactFile = buildArtifactFile(file, credentials)
        logger.info("The file with sha256 [$sha256] will be stored!")
        storageService.store(sha256, artifactFile, credentials)
        return ResponseBuilder.success()
    }

    private fun buildArtifactFile(file: MultipartFile, credentials: StorageCredentials): ArtifactFile {
        val fileName = file.originalFilename
        return if (fileName.isNullOrEmpty()) {
            ArtifactFileFactory.build(file, credentials)
        } else {
            val filepath: String = credentials.upload.location + "/" + fileName
            ArtifactFileFactory.build(file, filepath)
        }
    }

    @GetMapping(BLOB_CHECK_URI)
    fun check(
        @RequestParam sha256: String,
        @RequestParam storageKey: String? = null,
        @RequestParam(required = false) repoType: String? = null
    ): Response<Boolean> {
        cnsService?.let {
            val repositoryType = repoType?.let { RepositoryType.ofValueOrDefault(repoType) }
            return ResponseBuilder.success(it.check(storageKey, sha256, repositoryType))
        }
        val credentials = baseCacheHandler.credentialsCache.get(storageKey.orEmpty())
        return ResponseBuilder.success(storageService.exist(sha256, credentials))
    }

    /**
     * 分块上传
     * A chunked upload is accomplished in three phases:
     * 1:Obtain a session ID (upload URL) (POST)
     * 2:Upload the chunks (PATCH)
     * 3:Close the session (PUT)
     */
    @PostMapping(BOLBS_UPLOAD_FIRST_STEP_URL)
    fun startBlobUpload(
        @RequestParam sha256: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam storageKey: String? = null
    ) {
        logger.info("The file with sha256 [$sha256] will be handled with chunked upload!")
        val credentials = baseCacheHandler.credentialsCache.get(storageKey.orEmpty())
        return blobChunkedService.obtainSessionIdForUpload(
            projectId,
            repoName,
            credentials,
            sha256
        )
    }

    @RequestMapping(
        method = [RequestMethod.PATCH],
        value = [BOLBS_UPLOAD_SECOND_STEP_URL]
    )
    fun uploadChunkedBlob(
        artifactFile: ArtifactFile,
        @RequestParam sha256: String,
        @RequestParam storageKey: String? = null,
        @PathVariable uuid: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ) {
        logger.info("The file with sha256 [$sha256] will be uploaded with $uuid")
        val credentials = baseCacheHandler.credentialsCache.get(storageKey.orEmpty())
        blobChunkedService.uploadChunkedFile(
            projectId,
            repoName,
            credentials,
            sha256,
            artifactFile,
            uuid
        )
    }

    @RequestMapping(
        method = [RequestMethod.PUT],
        value = [BOLBS_UPLOAD_SECOND_STEP_URL]
    )
    fun finishBlobUpload(
        artifactFile: ArtifactFile,
        @RequestParam sha256: String,
        @RequestParam storageKey: String? = null,
        @RequestParam size: Long? = null,
        @RequestParam md5: String? = null,
        @PathVariable uuid: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ) {
        logger.info("The file (sha256 [$sha256], size [$size], md5 [$md5]) will be finished with $uuid")
        val credentials = baseCacheHandler.credentialsCache.get(storageKey.orEmpty())
        blobChunkedService.finishChunkedUpload(
            projectId = projectId,
            repoName = repoName,
            credentials = credentials,
            sha256 = sha256,
            artifactFile = artifactFile,
            uuid = uuid,
            size = size,
            md5 = md5
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BlobReplicaController::class.java)
        private const val MAX_CACHE_COUNT = 10L
        private const val CACHE_EXPIRE_MINUTES = 5L
    }
}
