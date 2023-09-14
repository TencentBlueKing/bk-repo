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
import com.tencent.bkrepo.ddc.exception.ReferenceIsMissingBlobsException
import com.tencent.bkrepo.ddc.pojo.Blob
import com.tencent.bkrepo.ddc.pojo.ContentHash
import com.tencent.bkrepo.ddc.serialization.CbObject
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
     * blob不存在时抛出[ReferenceIsMissingBlobsException]
     */
    fun getReferencedBlobs(projectId: String, repoName: String, cb: CbObject): List<Blob> {
        val objectsToVisit = ArrayDeque<CbObject>()
        val blobs = ArrayList<Blob>()
        val unresolvedBlobs = ArrayList<ContentHash>()
        objectsToVisit.offer(cb)

        while (objectsToVisit.isNotEmpty()) {
            objectsToVisit.poll().iterateAttachments { field ->
                val fieldAttachment = field.asAttachment()
                if (field.isBinaryAttachment()) {
                    val contentId = fieldAttachment.toString()
                    blobService
                        .getSmallestBlobByContentId(projectId, repoName, contentId)
                        ?.let { blobs.add(it) }
                        ?: unresolvedBlobs.add(ContentHash(fieldAttachment.toByteArray()))
                } else if (field.isObjectAttachment()) {
                    val contentHash = ContentHash(fieldAttachment.toByteArray())
                    resolveObject(projectId, repoName, contentHash)?.let {
                        blobs.add(it.first)
                        objectsToVisit.offer(it.second)
                    } ?: unresolvedBlobs.add(contentHash)
                } else {
                    throw NotImplementedException("Unknown attachment type for field $field")
                }
            }
        }

        if (unresolvedBlobs.isNotEmpty()) {
            throw ReferenceIsMissingBlobsException(unresolvedBlobs)
        }

        return blobs
    }

    /**
     * 根据[blobId]获取其关联的数据，并转化为CbObject
     *
     * @return 找不到[blobId]关联的数据时返回null
     */
    private fun resolveObject(projectId: String, repoName: String, blobId: ContentHash): Pair<Blob, CbObject>? {
        return try {
            val blob = blobService.getBlob(projectId, repoName, blobId.toString())
            val blobBytes = blobService.loadBlob(blob).readBytes()
            val cb = CbObject(ByteBuffer.wrap(blobBytes))
            Pair(blob, cb)
        } catch (_: BlobNotFoundException) {
            null
        }
    }
}
