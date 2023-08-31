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
import com.tencent.bkrepo.ddc.exception.PartialReferenceResolveException
import com.tencent.bkrepo.ddc.exception.ReferenceIsMissingBlobsException
import com.tencent.bkrepo.ddc.model.TDdcRef
import com.tencent.bkrepo.ddc.pojo.ContentHash
import com.tencent.bkrepo.ddc.pojo.CreateRefResponse
import com.tencent.bkrepo.ddc.pojo.Reference
import com.tencent.bkrepo.ddc.repository.RefRepository
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.utils.DdcUtils.fullPath
import com.tencent.bkrepo.ddc.utils.hasAttachments
import com.tencent.bkrepo.repository.api.NodeClient
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ReferenceService(
    private val ddcProperties: DdcProperties,
    private val blobService: BlobService,
    private val refResolver: ReferenceResolver,
    private val refRepository: RefRepository,
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
            id = null,
            createdBy = userId,
            createdDate = now,
            lastModifiedBy = userId,
            lastModifiedDate = now,
            lastAccessDate = now, // TODO 更新lastAccessDate
            projectId = ref.projectId,
            repoName = ref.repoName,
            bucket = ref.bucket,
            key = ref.key.toString(),
            finalized = ref.finalized!!,
            blobId = ref.blobId!!.toString(),
            inlineBlob = Binary(inlineBlob),
            expireDate = null // TODO 设置expireDate
        )
        refRepository.replace(tRef)
        return Reference.from(tRef)
    }

    fun getReference(
        projectId: String,
        repoName: String,
        bucket: String,
        key: String,
        includePayload: Boolean = true,
        checkFinalized: Boolean = true,
    ): Reference? {
        val tRef = refRepository.find(projectId, repoName, bucket, key, includePayload) ?: return null
        if (checkFinalized && !tRef.finalized) {
            throw BadRequestException(
                CommonMessageCode.PARAMETER_INVALID, "Object ${tRef.bucket} ${tRef.key} is not finalized."
            )
        }

        val payload = if (tRef.inlineBlob == null) {
            val repo = ArtifactContextHolder.getRepoDetail(ArtifactContextHolder.RepositoryId(projectId, repoName))
            nodeClient.getNodeDetail(projectId, repoName, tRef.fullPath()).data?.let {
                storageManager.loadArtifactInputStream(it, repo.storageCredentials)?.readBytes()
            }
        } else {
            tRef.inlineBlob!!.data
        }

        if (payload == null) {
            logger.warn("Blob was null when attempting to fetch ${tRef.repoName} ${tRef.bucket} ${tRef.key}")
            return null
        }

        val ref = Reference.from(tRef)
        ref.inlineBlob = payload
        return ref
    }

    fun finalize(ref: Reference, cbObject: CbObject): CreateRefResponse {
        blobService.addRefToBlobs(ref, setOf(ref.blobId!!.toString()))
        var missingRefs = emptyList<ContentHash>()
        var missingBlobs = emptyList<ContentHash>()
        if (cbObject.hasAttachments()) {
            try {
                val blobs = refResolver.getReferencedBlobs(ref.projectId, ref.repoName, cbObject)
                blobService.addRefToBlobs(ref, blobs.mapTo(HashSet()) { it.toString() })
            } catch (e: PartialReferenceResolveException) {
                missingRefs = e.unresolvedReferences
            } catch (e: ReferenceIsMissingBlobsException) {
                missingBlobs = e.missingBlobs
            }
        }

        if (missingRefs.isEmpty() && missingBlobs.isEmpty()) {
            refRepository.finalize(ref.projectId, ref.repoName, ref.bucket, ref.key.toString())
        }

        return CreateRefResponse((missingRefs + missingBlobs).mapTo(HashSet()) { it.toString() })
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReferenceService::class.java)
    }
}
