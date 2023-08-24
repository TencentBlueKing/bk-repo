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

import com.tencent.bkrepo.ddc.exception.BlobNotFoundException
import com.tencent.bkrepo.ddc.exception.NotImplementedException
import com.tencent.bkrepo.ddc.exception.PartialReferenceResolveException
import com.tencent.bkrepo.ddc.exception.ReferenceIsMissingBlobsException
import com.tencent.bkrepo.ddc.pojo.Attachment
import com.tencent.bkrepo.ddc.pojo.Blob
import com.tencent.bkrepo.ddc.pojo.BlobAttachment
import com.tencent.bkrepo.ddc.pojo.ContentHash
import com.tencent.bkrepo.ddc.pojo.ContentIdAttachment
import com.tencent.bkrepo.ddc.pojo.ObjectAttachment
import com.tencent.bkrepo.ddc.serialization.CbObject
import com.tencent.bkrepo.ddc.serialization.IoHash
import com.tencent.bkrepo.ddc.utils.isBinaryAttachment
import com.tencent.bkrepo.ddc.utils.isObjectAttachment
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.util.ArrayDeque

@Service
class ReferenceResolver(
    private val blobService: BlobService
) {

    /**
     * 获取[cb]直接与间接引用的所有blob，保证blob存在
     * blob不存在时抛出[PartialReferenceResolveException]或[ReferenceIsMissingBlobsException]
     */
    fun getReferencedBlobs(projectId: String, repoName: String, cb: CbObject): List<Blob> {
        val blobIdsOfContentAttachment = ArrayList<ContentHash>()
        val blobIdToContentIdMap = HashMap<ContentHash, ContentHash>()
        val blobIds = ArrayList<ContentHash>()

        for (attachment in getAttachments(projectId, repoName, cb)) {
            if (attachment is ContentIdAttachment) {
                blobIdsOfContentAttachment.addAll(attachment.blobs.map { it.blobId })
                attachment.blobs.forEach { blobIdToContentIdMap[it.blobId] = attachment.hash }
            } else {
                blobIds.add(attachment.hash)
            }
        }

        val blobsOfContentAttachment = blobService
            .getBlobByBlobIds(projectId, repoName, blobIdsOfContentAttachment.map { it.toString() })
        val existsBlobIdsOfContentAttachment = blobsOfContentAttachment.map { it.blobId }

        val unresolvedContentIds = blobIdsOfContentAttachment
            .filter { it !in existsBlobIdsOfContentAttachment }
            .map { blobIdToContentIdMap[it]!! }
        if (unresolvedContentIds.isNotEmpty()) {
            throw PartialReferenceResolveException(unresolvedContentIds)
        }

        val blobs = blobService.getBlobByBlobIds(projectId, repoName, blobIds.map { it.toString() })
        val existsBlobIds = blobs.map { it.blobId }
        val unresolvedBlobIds = blobIds.filter { it !in existsBlobIds }
        if (unresolvedBlobIds.isNotEmpty()) {
            throw ReferenceIsMissingBlobsException(unresolvedBlobIds)
        }
        return blobs + blobsOfContentAttachment
    }

    /**
     * 获取[cb]直接与渐渐引用的所有attachment
     */
    fun getAttachments(projectId: String, repoName: String, cb: CbObject): List<Attachment> {
        val objectsToVisit = ArrayDeque<CbObject>()
        val attachments = ArrayList<Attachment>()
        val unresolvedBlobs = ArrayList<ContentHash>()
        objectsToVisit.offer(cb)

        while (objectsToVisit.isNotEmpty()) {
            objectsToVisit.poll().iterateAttachments { field ->
                val fieldAttachment = field.asAttachment()
                if (field.isBinaryAttachment()) {
                    attachments.add(resolveBinaryAttachment(projectId, repoName, fieldAttachment))
                } else if (field.isObjectAttachment()) {
                    val contentHash = ContentHash(fieldAttachment.toByteArray())
                    attachments.add(ObjectAttachment(contentHash))
                    val cbObject = resolveObjectAttachment(projectId, repoName, contentHash)
                    cbObject?.let { objectsToVisit.offer(it) } ?: unresolvedBlobs.add(contentHash)
                } else {
                    throw NotImplementedException("Unknown attachment type for field $field")
                }
            }
        }

        if (unresolvedBlobs.isNotEmpty()) {
            throw ReferenceIsMissingBlobsException(unresolvedBlobs)
        }
        return attachments
    }

    /**
     * 获取[fieldAttachment]关联的所有二进制缓存blob引用
     */
    private fun resolveBinaryAttachment(projectId: String, repoName: String, fieldAttachment: IoHash): Attachment {
        val contentIdByteArray = fieldAttachment.toByteArray()
        val contentId = fieldAttachment.toString()
        val blobs = blobService.getBlobsByContentId(projectId, repoName, contentId)
        // blobs中是否只包含了未压缩的缓存
        val onlyContainUncompressed = blobs.size == 1 && blobs[0].blobId.toString() == contentId

        return if (!onlyContainUncompressed && blobs.isNotEmpty()) {
            ContentIdAttachment(ContentHash(contentIdByteArray), blobs)
        } else {
            BlobAttachment(ContentHash(contentIdByteArray))
        }
    }

    /**
     * 根据[blobId]获取其关联的数据，并转化未CbObject
     *
     * @return 找不到[blobId]关联的数据时返回null
     */
    private fun resolveObjectAttachment(projectId: String, repoName: String, blobId: ContentHash): CbObject? {
        try {
            val bytes = blobService.loadBlob(projectId, repoName, blobId.toString()).readBytes()
            return CbObject(ByteBuffer.wrap(bytes))
        } catch (_: BlobNotFoundException) {
        }
        return null
    }
}
