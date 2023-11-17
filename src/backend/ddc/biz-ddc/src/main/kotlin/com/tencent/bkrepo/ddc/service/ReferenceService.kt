/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.ddc.service

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.ddc.config.DdcProperties
import com.tencent.bkrepo.ddc.exception.ReferenceIsMissingBlobsException
import com.tencent.bkrepo.ddc.model.TDdcLegacyRef
import com.tencent.bkrepo.ddc.model.TDdcRef
import com.tencent.bkrepo.ddc.pojo.ContentHash
import com.tencent.bkrepo.ddc.pojo.CreateRefResponse
import com.tencent.bkrepo.ddc.pojo.RefId
import com.tencent.bkrepo.ddc.pojo.Reference
import com.tencent.bkrepo.ddc.repository.LegacyRefRepository
import com.tencent.bkrepo.ddc.repository.RefBaseRepository
import com.tencent.bkrepo.ddc.repository.RefRepository
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.utils.hasAttachments
import com.tencent.bkrepo.repository.api.NodeClient
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.time.LocalDateTime

@Service
class ReferenceService(
    private val ddcProperties: DdcProperties,
    private val blobService: BlobService,
    private val refResolver: ReferenceResolver,
    private val refRepository: RefRepository,
    private val legacyRefRepository: LegacyRefRepository,
    private val nodeClient: NodeClient,
    private val storageManager: StorageManager,
) {
    fun create(ref: Reference): Reference {
        val inlineBlob = if (ref.inlineBlob!!.size > ddcProperties.inlineBlobMaxSize.toBytes()) {
            null
        } else {
            ref.inlineBlob
        }

        val userId = SecurityUtils.getUserId()
        val now = LocalDateTime.now()
        val tRef = TDdcRef(
            createdBy = userId,
            createdDate = now,
            lastModifiedBy = userId,
            lastModifiedDate = now,
            lastAccessDate = now,
            projectId = ref.projectId,
            repoName = ref.repoName,
            bucket = ref.bucket,
            key = ref.key.toString(),
            finalized = ref.finalized!!,
            blobId = ref.blobId!!.toString(),
            inlineBlob = inlineBlob?.let { Binary(it) },
            expireDate = null // TODO 设置expireDate
        )
        refRepository.createIfNotExists(tRef)
        return Reference.from(tRef)
    }

    fun createLegacyReference(ref: Reference): Reference {
        val userId = SecurityUtils.getUserId()
        val now = LocalDateTime.now()
        val tRef = TDdcLegacyRef(
            createdBy = userId,
            createdDate = now,
            lastModifiedBy = userId,
            lastModifiedDate = now,
            lastAccessDate = now,
            projectId = ref.projectId,
            repoName = ref.repoName,
            bucket = ref.bucket,
            key = ref.key.toString(),
            contentHash = ref.blobId!!.toString(),
        )
        legacyRefRepository.createIfNotExists(tRef)
        return Reference.from(tRef)
    }

    fun getLegacyReference(
        projectId: String,
        repoName: String,
        bucket: String,
        key: String,
    ): Reference? {
        return legacyRefRepository.find(projectId, repoName, bucket, key)?.let { Reference.from(it) }
    }

    fun getReference(
        projectId: String,
        repoName: String,
        bucket: String,
        key: String,
        checkFinalized: Boolean = true,
        includePayload: Boolean = true,
    ): Reference? {
        val tRef = refRepository.find(projectId, repoName, bucket, key, includePayload) ?: return null
        if (checkFinalized && !tRef.finalized) {
            throw BadRequestException(
                CommonMessageCode.PARAMETER_INVALID, "Object ${tRef.bucket} ${tRef.key} is not finalized."
            )
        }

        val ref = Reference.from(tRef)
        if (ref.inlineBlob == null) {
            val repo = ArtifactContextHolder.getRepoDetail(ArtifactContextHolder.RepositoryId(projectId, repoName))
            ref.inlineBlob = nodeClient.getNodeDetail(projectId, repoName, ref.fullPath()).data?.let {
                storageManager.loadArtifactInputStream(it, repo.storageCredentials)?.readBytes()
            }
        }

        return if (ref.inlineBlob == null) {
            logger.warn("Blob was null when attempting to fetch ${ref.repoName} ${ref.bucket} ${ref.key}")
            null
        } else {
            ref
        }
    }

    fun deleteReference(
        projectId: String,
        repoName: String,
        bucket: String,
        key: String,
        legacy: Boolean = false
    ) {
        if (getRepository(legacy).delete(projectId, repoName, bucket, key).deletedCount == 0L) {
            throw BadRequestException(
                CommonMessageCode.PARAMETER_INVALID,
                "Deleted 0 records, most likely the object did not exist"
            )
        }
        blobService.removeRefFromBlobs(projectId, repoName, bucket, key)
    }

    fun finalize(ref: Reference, payload: ByteArray): CreateRefResponse {
        val cbObject = CbObject(ByteBuffer.wrap(payload))
        var missingBlobs = emptyList<ContentHash>()
        if (cbObject.hasAttachments()) {
            try {
                val blobs = refResolver.getReferencedBlobs(ref.projectId, ref.repoName, cbObject)
                blobService.addRefToBlobs(ref, blobs.mapTo(HashSet()) { it.blobId.toString() })
            } catch (e: ReferenceIsMissingBlobsException) {
                missingBlobs = e.missingBlobs
            }
        }

        if (missingBlobs.isEmpty()) {
            refRepository.finalize(ref.projectId, ref.repoName, ref.bucket, ref.key.toString())
        }

        return CreateRefResponse((missingBlobs).mapTo(HashSet()) { it.toString() })
    }

    fun updateLastAccess(refId: RefId, lastAccessDate: LocalDateTime) {
        getRepository(refId.legacy).updateLastAccess(refId, lastAccessDate)
    }

    private fun getRepository(legacy: Boolean): RefBaseRepository<*> = if (legacy) {
        legacyRefRepository
    } else {
        refRepository
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReferenceService::class.java)
    }
}
