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

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.ddc.exception.BlobNotFoundException
import com.tencent.bkrepo.ddc.pojo.Blob
import com.tencent.bkrepo.ddc.pojo.Reference
import com.tencent.bkrepo.ddc.utils.NODE_METADATA_KEY_BLOB_ID
import com.tencent.bkrepo.ddc.utils.NODE_METADATA_KEY_CONTENT_ID
import com.tencent.bkrepo.ddc.utils.NODE_TO_BLOB_SELECT
import com.tencent.bkrepo.ddc.utils.toBlob
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.springframework.stereotype.Service

@Service
class BlobService(
    private val nodeClient: NodeClient,
    private val metadataClient: MetadataClient,
    private val storageService: StorageService
) {
    fun loadBlob(projectId: String, repoName: String, blobId: String): ArtifactInputStream {
        val queryModel = NodeQueryBuilder()
            .projectId(projectId)
            .repoName(repoName)
            .metadata(NODE_METADATA_KEY_BLOB_ID, blobId)
            .select(*NODE_TO_BLOB_SELECT)
            .page(DEFAULT_PAGE_NUMBER, 1)
            .build()
        val res = nodeClient.search(queryModel)
        val records = res.data?.records
        if (records.isNullOrEmpty()) {
            throw BlobNotFoundException(projectId, repoName, blobId)
        }
        val blob = records[0].toBlob()
        val repo = ArtifactContextHolder.getRepoDetail(ArtifactContextHolder.RepositoryId(projectId, repoName))
        return storageService.load(blob.sha256, Range.full(blob.size), repo.storageCredentials)
            ?: throw BlobNotFoundException(projectId, repoName, blobId)
    }

    fun getBlobsByContentId(projectId: String, repoName: String, contentId: String): List<Blob> {
        val queryModel = NodeQueryBuilder()
            .projectId(projectId)
            .repoName(repoName)
            .metadata(NODE_METADATA_KEY_CONTENT_ID, contentId)
            .select(*NODE_TO_BLOB_SELECT)
            .build()
        return search(queryModel).map { it.toBlob() }
    }

    fun getBlobByBlobIds(projectId: String, repoName: String, blobIds: Collection<String>): List<Blob> {
        val queryModel = NodeQueryBuilder()
            .projectId(projectId)
            .repoName(repoName)
            .metadata(NODE_METADATA_KEY_BLOB_ID, blobIds, OperationType.IN)
            .select(*NODE_TO_BLOB_SELECT)
            .build()
        return search(queryModel).map { it.toBlob() }
    }

    fun addRefToBlobs(ref: Reference, blobs: Set<String>) {
        // TODO
    }

    private fun search(queryModel: QueryModel): List<Map<String, Any?>> {
        var res: Response<Page<Map<String, Any?>>>
        var model = queryModel
        val result = ArrayList<Map<String, Any?>>()
        do {
            res = nodeClient.search(queryModel)
            if (res.isNotOk()) {
                throw ErrorCodeException(
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    messageCode = CommonMessageCode.SYSTEM_ERROR,
                    params = arrayOf(res.message.toString())
                )
            }

            val records = res.data?.records
            if (records?.isNotEmpty() == true) {
                result.addAll((records))
            }
            model = model.copy(page = PageLimit(pageNumber = model.page.pageNumber + 1))
        } while (model.page.pageSize == res.data?.records?.size)
        return result
    }
}
