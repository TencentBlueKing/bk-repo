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

package com.tencent.bkrepo.ddc.artifact.repository

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.ddc.artifact.CompressedBlobArtifactInfo
import com.tencent.bkrepo.ddc.artifact.ReferenceArtifactInfo
import com.tencent.bkrepo.ddc.component.RefDownloadListener
import com.tencent.bkrepo.ddc.event.RefDownloadedEvent
import com.tencent.bkrepo.ddc.exception.BlobNotFoundException
import com.tencent.bkrepo.ddc.exception.NotImplementedException
import com.tencent.bkrepo.ddc.exception.ReferenceIsMissingBlobsException
import com.tencent.bkrepo.ddc.metrics.DdcMeterBinder
import com.tencent.bkrepo.ddc.pojo.Blob
import com.tencent.bkrepo.ddc.pojo.ContentHash
import com.tencent.bkrepo.ddc.pojo.Reference
import com.tencent.bkrepo.ddc.pojo.UploadCompressedBlobResponse
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.service.BlobService
import com.tencent.bkrepo.ddc.service.ReferenceResolver
import com.tencent.bkrepo.ddc.service.ReferenceService
import com.tencent.bkrepo.ddc.utils.BlakeUtils.blake3
import com.tencent.bkrepo.ddc.utils.BlakeUtils.hex
import com.tencent.bkrepo.ddc.utils.DdcUtils
import com.tencent.bkrepo.ddc.utils.MEDIA_TYPE_JUPITER_INLINED_PAYLOAD
import com.tencent.bkrepo.ddc.utils.MEDIA_TYPE_UNREAL_COMPACT_BINARY
import com.tencent.bkrepo.ddc.utils.MEDIA_TYPE_UNREAL_UNREAL_COMPRESSED_BUFFER
import com.tencent.bkrepo.ddc.utils.NODE_METADATA_KEY_BLOB_ID
import com.tencent.bkrepo.ddc.utils.NODE_METADATA_KEY_CONTENT_ID
import com.tencent.bkrepo.ddc.utils.isAttachment
import com.tencent.bkrepo.ddc.utils.isBinaryAttachment
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit.NANOSECONDS

@Component
class DdcLocalRepository(
    private val referenceService: ReferenceService,
    private val refResolver: ReferenceResolver,
    private val blobService: BlobService,
    private val refDownloadListener: RefDownloadListener,
    private val ddcMeterBinder: DdcMeterBinder,
) : LocalRepository() {
    override fun onUploadBefore(context: ArtifactUploadContext) {
        super.onUploadBefore(context)
        var uploadBlake3: String? = null
        val artifactInfo = context.artifactInfo
        if (artifactInfo is ReferenceArtifactInfo && !artifactInfo.legacy) {
            uploadBlake3 = artifactInfo.inlineBlobHash!!
        }
        if (uploadBlake3 != null && uploadBlake3 != context.getStreamArtifactFile().blake3().hex()) {
            throw ErrorCodeException(ArtifactMessageCode.DIGEST_CHECK_FAILED, "blake3")
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val artifactInfo = context.artifactInfo
        val startTime = System.nanoTime()
        if (artifactInfo is ReferenceArtifactInfo && artifactInfo.legacy) {
            onUploadLegacyReference(context)
            ddcMeterBinder.legacyRefStoreTimer.record(System.nanoTime() - startTime, NANOSECONDS)
        } else if (artifactInfo is ReferenceArtifactInfo) {
            onUploadReference(context)
            ddcMeterBinder.refStoreTimer.record(System.nanoTime() - startTime, NANOSECONDS)
        } else {
            onUploadBlob(context)
        }
    }

    override fun onDownloadBefore(context: ArtifactDownloadContext) {
        super.onDownloadBefore(context)
        val artifactInfo = context.artifactInfo
        if (artifactInfo is ReferenceArtifactInfo) {
            ddcMeterBinder.incCacheCount(artifactInfo.projectId, artifactInfo.repoName)
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val artifactInfo = context.artifactInfo
        return if (artifactInfo is ReferenceArtifactInfo && artifactInfo.legacy) {
            onDownloadLegacyReference(context)
        } else if (artifactInfo is ReferenceArtifactInfo) {
            onDownloadReference(context)
        } else {
            val startTime = System.nanoTime()
            onDownloadBlob(context)?.apply {
                ddcMeterBinder.blobLoadTimer.record(System.nanoTime() - startTime, NANOSECONDS)
            }
        }
    }

    override fun onDownloadSuccess(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        throughput: Throughput
    ) {
        super.onDownloadSuccess(context, artifactResource, throughput)
        val artifactInfo = context.artifactInfo
        if (artifactInfo is ReferenceArtifactInfo) {
            ddcMeterBinder.incCacheHitCount(artifactInfo.projectId, artifactInfo.repoName)
        }
    }

    private fun onUploadLegacyReference(context: ArtifactUploadContext) {
        with(context) {
            val artifactInfo = context.artifactInfo as ReferenceArtifactInfo
            val sha1 = context.request.getHeader(HEADER_NAME_HASH_SHA1)
                ?: throw BadRequestException(
                    CommonMessageCode.PARAMETER_INVALID, "Missing expected header $HEADER_NAME_HASH_SHA1"
                )
            val artifactFileSha1 = getArtifactSha1()
            if (sha1.toLowerCase() != artifactFileSha1) {
                throw BadRequestException(
                    CommonMessageCode.PARAMETER_INVALID,
                    "Incorrect hash, got hash \"${sha1}\" " +
                            "but hash of content was determined to be \"${artifactFileSha1}\""
                )
            }
            val blobIdByteArray = getStreamArtifactFile().blake3()
            val blobId = blobIdByteArray.hex()
            val blobIdContentHash = ContentHash(blobIdByteArray)
            // 创建blob
            val blobFullPath = "/${DdcUtils.DIR_BLOBS}/$blobId"
            val createRequest = buildBlobNodeCreateRequest(
                blobId,
                blobId,
                artifactInfo,
                blobFullPath,
                getArtifactFile(),
                context.userId
            )
            storageManager.storeArtifactFile(createRequest, getArtifactFile(), storageCredentials)
            val blob = Blob(
                projectId = projectId,
                repoName = repoName,
                sha256 = getArtifactSha256(),
                fullPath = blobFullPath,
                size = getArtifactFile().getSize(),
                blobId = blobIdContentHash,
                contentId = blobIdContentHash,
                sha1 = artifactFileSha1,
            )
            blobService.create(blob)

            // 创建Ref
            val ref = referenceService.createLegacyReference(
                Reference(
                    projectId = projectId,
                    repoName = repoName,
                    bucket = artifactInfo.bucket,
                    key = artifactInfo.refKey,
                    finalized = true,
                    blobId = blobIdContentHash,
                    legacy = true
                )
            )

            blobService.addRefToBlobs(ref, setOf(blobId))
        }
    }

    private fun onUploadReference(context: ArtifactUploadContext) {
        val contentType = context.request.contentType
        val artifactInfo = context.artifactInfo as ReferenceArtifactInfo
        when (contentType) {
            MEDIA_TYPE_UNREAL_COMPACT_BINARY -> {
                val payload = context.getArtifactFile().getInputStream().readBytes()
                val ref = referenceService.create(Reference.from(artifactInfo, payload))
                if (ref.inlineBlob == null) {
                    // inlineBlob为null时表示inlineBlob过大，需要存到文件中
                    val nodeCreateRequest = buildRefNodeCreateRequest(context)
                    storageManager.storeArtifactFile(
                        nodeCreateRequest, context.getArtifactFile(), context.storageCredentials
                    )
                }
                val res = referenceService.finalize(ref, payload)
                HttpContextHolder.getResponse().writer.println(res.toJsonString())
            }

            else -> throw BadRequestException(
                CommonMessageCode.PARAMETER_INVALID, "Unknown request type $contentType"
            )
        }
    }

    private fun buildRefNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo as ReferenceArtifactInfo
        val metadata = ArrayList<MetadataModel>()
        metadata.add(
            MetadataModel(
                key = NODE_METADATA_KEY_BLOB_ID,
                value = artifactInfo.inlineBlobHash!!,
                system = true
            )
        )

        return buildNodeCreateRequest(context).copy(
            overwrite = true,
            nodeMetadata = metadata
        )
    }

    private fun onUploadBlob(context: ArtifactUploadContext) {
        with(context) {
            val artifactInfo = artifactInfo as CompressedBlobArtifactInfo
            // TODO 校验解压后blob hash与contentId是否相等
            // TODO 改为读取流时直接计算blake3，避免重复读流
            artifactInfo.compressedContentId = getStreamArtifactFile().blake3().hex()

            val createRequest = buildBlobNodeCreateRequest(
                artifactInfo.compressedContentId!!,
                artifactInfo.contentId,
                artifactInfo,
                artifactInfo.getArtifactFullPath(),
                getArtifactFile(),
                userId
            )
            storageManager.storeArtifactFile(createRequest, getArtifactFile(), storageCredentials)
            blobService.create(Blob.from(artifactInfo, getArtifactSha256(), getArtifactFile().getSize()))
            HttpContextHolder
                .getResponse()
                .writer
                .println(UploadCompressedBlobResponse(artifactInfo.contentId).toJsonString())
        }
    }

    private fun buildBlobNodeCreateRequest(
        blobId: String,
        contentId: String,
        artifactInfo: ArtifactInfo,
        fullPath: String,
        artifactFile: ArtifactFile,
        userId: String
    ): NodeCreateRequest {
        val metadata = ArrayList<MetadataModel>()
        metadata.add(
            MetadataModel(
                key = NODE_METADATA_KEY_BLOB_ID,
                value = blobId,
                system = true
            )
        )
        metadata.add(
            MetadataModel(
                key = NODE_METADATA_KEY_CONTENT_ID,
                value = contentId,
                system = true
            )
        )

        return NodeCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            folder = false,
            fullPath = fullPath,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            operator = userId,
            overwrite = true,
            nodeMetadata = metadata,
        )
    }

    private fun onDownloadLegacyReference(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val startTime = System.nanoTime()
            val artifactInfo = context.artifactInfo as ReferenceArtifactInfo
            val ref = referenceService.getLegacyReference(
                artifactInfo.projectId, artifactInfo.repoName, artifactInfo.bucket, artifactInfo.refKey.toString()
            ) ?: return null

            refDownloadListener.onRefDownloaded(RefDownloadedEvent(ref, SecurityUtils.getUserId()))
            val blob = blobService.findBlob(ref.projectId, ref.repoName, ref.blobId.toString()) ?: return null
            response.addHeader(HEADER_NAME_HASH, ref.blobId.toString())
            response.addHeader(HEADER_NAME_HASH_SHA1, blob.sha1!!)
            ddcMeterBinder.legacyRefLoadTimer.record(System.nanoTime() - startTime, NANOSECONDS)
            return blobToArtifactResource(context, blob, MediaTypes.APPLICATION_OCTET_STREAM)
        }
    }

    private fun onDownloadReference(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val startTime = System.nanoTime()
            val artifactInfo = context.artifactInfo as ReferenceArtifactInfo
            val ref = referenceService.getReference(
                projectId, repoName, artifactInfo.bucket, artifactInfo.refKey.toString()
            ) ?: return null
            refDownloadListener.onRefDownloaded(RefDownloadedEvent(ref, SecurityUtils.getUserId()))
            response.addHeader(HEADER_NAME_HASH, ref.blobId.toString())
            response.addHeader(HEADER_NAME_LAST_ACCESS, ref.lastAccessDate!!.format(DATE_TIME_FORMATTER))

            return when (val responseType = response.contentType) {
                MEDIA_TYPE_UNREAL_COMPACT_BINARY -> {
                    val ais = ArtifactInputStream(
                        ByteArrayInputStream(ref.inlineBlob),
                        Range.full(ref.inlineBlob!!.size.toLong())
                    )
                    ArtifactResource(ais, artifactInfo.getResponseName()).apply {
                        contentType = responseType
                        ddcMeterBinder.refLoadTimer.record(System.nanoTime() - startTime, NANOSECONDS)
                    }
                }

                MEDIA_TYPE_JUPITER_INLINED_PAYLOAD -> {
                    onInlineDownload(context, ref.inlineBlob!!, responseType)?.apply {
                        ddcMeterBinder.refInlineLoadTimer.record(System.nanoTime() - startTime, NANOSECONDS)
                    }
                }

                else -> throw NotImplementedException("Unknown expected response type $responseType")
            }
        }
    }

    private fun onDownloadBlob(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val artifactInfo = context.artifactInfo as CompressedBlobArtifactInfo
            val acceptContentType = request.getHeaders("Accept").toList()
            val blob = blobService.getSmallestBlobByContentId(projectId, repoName, artifactInfo.contentId)
                ?: return null
            val blobId = blob.blobId.toString()
            artifactInfo.compressedContentId = blobId

            val responseType = if (blobId == artifactInfo.contentId) {
                MediaTypes.APPLICATION_OCTET_STREAM
            } else {
                MEDIA_TYPE_UNREAL_UNREAL_COMPRESSED_BUFFER
            }

            if (acceptContentType.isNotEmpty() &&
                !acceptContentType.contains("*/*") &&
                !acceptContentType.contains(responseType)
            ) {
                throw ErrorCodeException(
                    status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    messageCode = CommonMessageCode.MEDIA_TYPE_UNSUPPORTED
                )
            }

            val blobInputStream = blobService.loadBlob(blob)
            val resource = ArtifactResource(blobInputStream, artifactInfo.getResponseName())
            resource.contentType = responseType
            return resource
        }
    }

    /**
     * 客户端调用获取ref的接口时，直接返回缓存内容
     */
    private fun onInlineDownload(
        context: ArtifactDownloadContext,
        refInlineBlob: ByteArray,
        responseType: String,
    ): ArtifactResource? {
        with(context) {
            val cb = CbObject(ByteBuffer.wrap(refInlineBlob))
            val (binaryAttachmentCount, attachmentCount) = countAttachment(cb)
            val artifactInfo = artifactInfo as ReferenceArtifactInfo

            // 只允许在ref只包含了单个binaryAttachment的情况下，直接通过ref接口直接获取缓存文件
            if (binaryAttachmentCount > 1 || attachmentCount > 1 || binaryAttachmentCount != attachmentCount) {
                throw BadRequestException(
                    CommonMessageCode.PARAMETER_INVALID,
                    "Object ${artifactInfo.bucket} ${artifactInfo.refKey} had more then 1 binary attachment field," +
                            " unable to inline this object. Use compact object response instead."
                )
            }

            return if (binaryAttachmentCount == 1) {
                loadReferencedBlob(context, cb, responseType)
            } else {
                val range = Range.full(refInlineBlob.size.toLong())
                val ais = ArtifactInputStream(ByteArrayInputStream(refInlineBlob), range)
                ArtifactResource(ais, artifactInfo.getResponseName()).apply { contentType = responseType }
            }
        }
    }

    private fun loadReferencedBlob(
        context: ArtifactDownloadContext, cb: CbObject, responseType: String
    ): ArtifactResource? {
        return try {
            val artifactInfo = context.artifactInfo as ReferenceArtifactInfo
            val blobs = refResolver.getReferencedBlobs(context.projectId, context.repoName, cb)
            if (blobs.size == 1) {
                context.response.addHeader(HEADER_NAME_INLINE_PAYLOAD_HASH, blobs[0].blobId.toString())
                blobToArtifactResource(context, blobs[0], responseType)
            } else if (blobs.isEmpty()) {
                null
            } else {
                throw BadRequestException(
                    CommonMessageCode.PARAMETER_INVALID,
                    "Object ${artifactInfo.bucket} ${artifactInfo.refKey} contained a content id " +
                            "which resolved to more then 1 blob, unable to inline this object. " +
                            "Use compact object response instead."
                )
            }
        } catch (e: ReferenceIsMissingBlobsException) {
            null
        }
    }

    private fun blobToArtifactResource(
        context: ArtifactDownloadContext,
        blob: Blob,
        responseType: String
    ): ArtifactResource? {
        with(context) {
            return try {
                val blobInputStream = blobService.loadBlob(blob)
                ArtifactResource(blobInputStream, artifactInfo.getResponseName()).apply { contentType = responseType }
            } catch (e: BlobNotFoundException) {
                null
            }
        }
    }

    private fun countAttachment(cb: CbObject): Pair<Int, Int> {
        var countOfAttachmentFields = 0
        var countOfBinaryAttachmentFields = 0
        cb.iterateAttachments {
            if (it.isBinaryAttachment()) {
                countOfBinaryAttachmentFields++
            }
            if (it.isAttachment()) {
                countOfAttachmentFields++
            }
        }

        return Pair(countOfBinaryAttachmentFields, countOfAttachmentFields)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DdcLocalRepository::class.java)
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss")
        const val HEADER_NAME_HASH = "X-Jupiter-IoHash"
        private const val HEADER_NAME_HASH_SHA1 = "X-Jupiter-Sha1"
        private const val HEADER_NAME_LAST_ACCESS = "X-Jupiter-LastAccess"
        private const val HEADER_NAME_INLINE_PAYLOAD_HASH = "X-Jupiter-InlinePayloadHash"
    }
}
