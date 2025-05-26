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

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.HttpStatus.NOT_FOUND
import com.tencent.bkrepo.common.api.constant.MediaTypes.APPLICATION_JSON
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.message.CommonMessageCode.PARAMETER_MISSING
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.memory.ByteArrayArtifactFile
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.ddc.artifact.ReferenceArtifactInfo
import com.tencent.bkrepo.ddc.artifact.repository.DdcLocalRepository
import com.tencent.bkrepo.ddc.component.RefDownloadListener
import com.tencent.bkrepo.ddc.config.DdcConfiguration.Companion.BEAN_NAME_REF_BATCH_EXECUTOR
import com.tencent.bkrepo.ddc.event.RefDownloadedEvent
import com.tencent.bkrepo.ddc.metrics.DdcMeterBinder
import com.tencent.bkrepo.ddc.pojo.BatchOp
import com.tencent.bkrepo.ddc.pojo.BatchOps
import com.tencent.bkrepo.ddc.pojo.BatchOpsResponse
import com.tencent.bkrepo.ddc.pojo.OpResponse
import com.tencent.bkrepo.ddc.pojo.OperationType
import com.tencent.bkrepo.ddc.pojo.RefKey
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.utils.BlakeUtils
import com.tencent.bkrepo.ddc.utils.BlakeUtils.hex
import com.tencent.bkrepo.ddc.utils.DdcUtils
import com.tencent.bkrepo.ddc.utils.writeBool
import com.tencent.bkrepo.common.metadata.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.util.concurrent.Future

@Service
class ReferenceArtifactService(
    private val referenceService: ReferenceService,
    private val refDownloadListener: RefDownloadListener,
    private val ddcMeterBinder: DdcMeterBinder,
    private val referenceResolver: ReferenceResolver,
    private val ddcLocalRepository: DdcLocalRepository,
    @Qualifier(BEAN_NAME_REF_BATCH_EXECUTOR)
    private val executor: ThreadPoolTaskExecutor
) : ArtifactService() {

    fun downloadRef(artifactInfo: ReferenceArtifactInfo) {
        repository.download(ArtifactDownloadContext())
    }

    fun createRef(artifactInfo: ReferenceArtifactInfo, file: ArtifactFile) {
        repository.upload(ArtifactUploadContext(file))
    }

    fun deleteRef(artifactInfo: ReferenceArtifactInfo) {
        with(artifactInfo) {
            referenceService.deleteReference(projectId, repoName, bucket, refKey.toString(), legacy)
        }
    }

    fun finalize(artifactInfo: ReferenceArtifactInfo) {
        with(artifactInfo) {
            val ref = referenceService.getReference(
                projectId, repoName, bucket, refKey.toString(), checkFinalized = false
            ) ?: throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "No blob when attempting to finalize")
            if (ref.blobId!!.toString() != artifactInfo.inlineBlobHash) {
                throw ErrorCodeException(ArtifactMessageCode.DIGEST_CHECK_FAILED, "blake3")
            }
            val res = referenceService.finalize(ref, ref.inlineBlob!!)
            HttpContextHolder.getResponse().setHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
            HttpContextHolder.getResponse().writer.println(res.toJsonString())
        }
    }

    /**
     * 批量操作
     */
    fun batch(projectId: String, repoName: String, ops: BatchOps): BatchOpsResponse {
        val results = HashMap<Int, Future<Pair<CbObject, Int>>>(ops.ops.size)
        val repo = ArtifactContextHolder.getRepoDetail()!!
        val userId = SecurityUtils.getUserId()
        for (op in ops.ops) {
            results[op.opId] = when (op.op) {
                OperationType.GET.name ->
                    executor.submit<Pair<CbObject, Int>> { getRef(projectId, repoName, op, userId) }
                OperationType.HEAD.name ->
                    executor.submit<Pair<CbObject, Int>> { headRef(projectId, repoName, op, userId) }
                OperationType.PUT.name ->
                    executor.submit<Pair<CbObject, Int>> { putRef(repo, op, userId) }
                else -> throw UnsupportedOperationException("unsupported op: ${op.op}")
            }
        }
        val opResponse = results.map {
            val result = it.value.get()
            OpResponse(it.key, result.first, result.second)
        }
        return BatchOpsResponse(opResponse)
    }

    private fun getRef(projectId: String, repoName: String, op: BatchOp, user: String): Pair<CbObject, Int> {
        val refFullKey = "$projectId/$repoName/${op.bucket}/${op.key}"
        return try {
            ddcMeterBinder.incCacheCount(projectId, repoName)
            val ref = referenceService.getReference(projectId, repoName, op.bucket, op.key)
                ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, refFullKey, status = NOT_FOUND)
            val refCb = CbObject(ByteBuffer.wrap(ref.inlineBlob!!))
            if (op.resolveAttachments) {
                referenceResolver.getReferencedBlobs(projectId, repoName, refCb)
            }
            refDownloadListener.onRefDownloaded(RefDownloadedEvent(ref, user))
            ddcMeterBinder.incCacheHitCount(projectId, repoName)
            logger.info("User[${user}] get ref [$refFullKey] success")
            return Pair(refCb, HttpStatus.OK.value)
        } catch (e: Exception) {
            DdcUtils.toError(e)
        }
    }

    private fun headRef(projectId: String, repoName: String, op: BatchOp, user: String): Pair<CbObject, Int> {
        val refFullKey = "$projectId/$repoName/${op.bucket}/${op.key}"
        return try {
            val ref = referenceService.getReference(projectId, repoName, op.bucket, op.key)
                ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, refFullKey, status = NOT_FOUND)
            if (!ref.finalized!!) {
                return Pair(CbObject.build { it.writeBool("exists", false) }, NOT_FOUND.value)
            }

            val refCb = CbObject(ByteBuffer.wrap(ref.inlineBlob!!))
            if (op.resolveAttachments) {
                referenceResolver.getReferencedBlobs(projectId, repoName, refCb)
            }

            logger.info("User[${user}] head ref [$refFullKey] success")
            return Pair(CbObject.build { it.writeBool("exists", true) }, HttpStatus.OK.value)
        } catch (e: Exception) {
            if (e is ErrorCodeException && e.status == NOT_FOUND) {
                Pair(CbObject.build { it.writeBool("exists", false) }, NOT_FOUND.value)
            } else {
                DdcUtils.toError(e)
            }
        }
    }

    private fun putRef(repo: RepositoryDetail, op: BatchOp, operator: String): Pair<CbObject, Int> {
        return try {
            // 检查payload参数是否正确
            if (op.payload == null || op.payload == CbObject.EMPTY) {
                throw ErrorCodeException(PARAMETER_MISSING, "Missing payload for operation: ${op.opId}")
            }

            if (op.payloadHash.isNullOrEmpty()) {
                throw ErrorCodeException(PARAMETER_MISSING, "Missing payload hash for operation: ${op.opId}")
            }

            val inlineBlob = op.payload.getView().array()
            val inlineBlobHash = BlakeUtils.hash(inlineBlob).hex()
            if (op.payloadHash != inlineBlobHash) {
                throw ErrorCodeException(ArtifactMessageCode.DIGEST_CHECK_FAILED, "blake3")
            }

            // 创建ref
            val artifactInfo = ReferenceArtifactInfo(
                projectId = repo.projectId,
                repoName = repo.name,
                bucket = op.bucket,
                refKey = RefKey.create(op.key),
                inlineBlobHash = inlineBlobHash
            )
            val artifactFile = ByteArrayArtifactFile(inlineBlob)
            val res = ddcLocalRepository.uploadReference(repo, artifactInfo, artifactFile, operator)
            return Pair(res.serialize(), HttpStatus.OK.value)
        } catch (e: Exception) {
            DdcUtils.toError(e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReferenceArtifactService::class.java)
    }
}
