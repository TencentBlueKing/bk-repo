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

import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.ddc.exception.BlobNotFoundException
import com.tencent.bkrepo.ddc.model.TDdcBlob
import com.tencent.bkrepo.ddc.pojo.Blob
import com.tencent.bkrepo.ddc.pojo.Reference
import com.tencent.bkrepo.ddc.repository.BlobRepository
import com.tencent.bkrepo.repository.api.NodeClient
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class BlobService(
    private val blobRepository: BlobRepository,
    private val nodeClient: NodeClient,
    private val storageManager: StorageManager
) {
    fun create(blob: Blob): Blob {
        with(blob) {
            val userId = SecurityUtils.getUserId()
            val now = LocalDateTime.now()
            val tBlob = TDdcBlob(
                createdBy = userId,
                createdDate = now,
                lastModifiedBy = userId,
                lastModifiedDate = now,
                projectId = projectId,
                repoName = repoName,
                blobId = blobId.toString(),
                contentId = contentId.toString(),
                sha256 = sha256,
                size = size,
                sha1 = sha1,
            )
            blobRepository.createIfNotExists(tBlob)

            return Blob.from(tBlob)
        }

    }

    fun getBlob(projectId: String, repoName: String, blobId: String): Blob {
        return blobRepository.findByBlobId(projectId, repoName, blobId)?.let { Blob.from(it) }
            ?: throw BlobNotFoundException(projectId, repoName, blobId)
    }

    fun findBlob(projectId: String, repoName: String, blobId: String): Blob? {
        return blobRepository.findByBlobId(projectId, repoName, blobId)?.let { Blob.from(it) }
    }
    fun loadBlob(projectId: String, repoName: String, blobId: String): ArtifactInputStream {
        val blob = blobRepository.findByBlobId(projectId, repoName, blobId)
            ?: throw BlobNotFoundException(projectId, repoName, blobId)
        return loadBlob(Blob.from(blob))
    }

    fun loadBlob(blob: Blob): ArtifactInputStream {
        val repo =
            ArtifactContextHolder.getRepoDetail(ArtifactContextHolder.RepositoryId(blob.projectId, blob.repoName))
        val node = nodeClient.getNodeDetail(blob.projectId, blob.repoName, blob.fullPath).data
            ?: throw BlobNotFoundException(blob.projectId, blob.repoName, blob.blobId.toString())
        return storageManager.loadArtifactInputStream(node, repo.storageCredentials)
            ?: throw BlobNotFoundException(blob.projectId, blob.repoName, blob.blobId.toString())
    }

    /**
     * 根据blob大小排序，返回最小的blob
     */
    fun getSmallestBlobByContentId(projectId: String, repoName: String, contentId: String): Blob? {
        return blobRepository.findSmallestByContentId(projectId, repoName, contentId)?.let { Blob.from(it) }
    }

    fun getBlobByBlobIds(projectId: String, repoName: String, blobIds: Collection<String>): List<Blob> {
        return blobRepository.findByBlobIds(projectId, repoName, blobIds.toSet()).map { Blob.from(it) }
    }

    fun addRefToBlobs(ref: Reference, blobIds: Set<String>) {
        blobRepository.addRefToBlob(ref.projectId, ref.repoName, ref.bucket, ref.key.toString(), blobIds)
    }

    fun removeRefFromBlobs(projectId: String, repoName: String, bucket: String, key: String) {
        blobRepository.removeRefFromBlob(projectId, repoName, bucket, key)
    }
}
