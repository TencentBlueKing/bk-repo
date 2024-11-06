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

package com.tencent.bkrepo.ddc.repository

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.ddc.model.TDdcBlob
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class BlobRepository : SimpleMongoDao<TDdcBlob>() {
    fun findSmallestByContentId(projectId: String, repoName: String, contentId: String): TDdcBlob? {
        val criteria = TDdcBlob::projectId.isEqualTo(projectId)
            .and(TDdcBlob::repoName.name).isEqualTo(repoName)
            .and(TDdcBlob::contentId.name).isEqualTo(contentId)
        val query = Query(criteria).with(Sort.by(Sort.Direction.ASC, TDdcBlob::size.name))
        return findOne(query)
    }

    fun findByBlobId(projectId: String, repoName: String, blobId: String): TDdcBlob? {
        val criteria = TDdcBlob::projectId.isEqualTo(projectId)
            .and(TDdcBlob::repoName.name).isEqualTo(repoName)
            .and(TDdcBlob::blobId.name).isEqualTo(blobId)
        val query = Query(criteria)
        return findOne(query)
    }

    fun findByBlobIds(projectId: String, repoName: String, blobIds: Set<String>): List<TDdcBlob> {
        val criteria = TDdcBlob::projectId.isEqualTo(projectId)
            .and(TDdcBlob::repoName.name).isEqualTo(repoName)
            .and(TDdcBlob::blobId.name).inValues(blobIds)
        val query = Query(criteria)
        return find(query)
    }

    fun createIfNotExists(blob: TDdcBlob): TDdcBlob? {
        return try {
            insert(blob)
        } catch (e: DuplicateKeyException) {
            val criteria = TDdcBlob::projectId.isEqualTo(blob.projectId)
                .and(TDdcBlob::repoName.name).isEqualTo(blob.repoName)
                .and(TDdcBlob::blobId.name).isEqualTo(blob.blobId)
            val query = Query(criteria)
            findOne(query)
        }
    }

    fun addRefToBlob(projectId: String, repoName: String, bucket: String, refKey: String, blobIds: Set<String>) {
        val criteria = TDdcBlob::projectId.isEqualTo(projectId)
            .and(TDdcBlob::repoName.name).isEqualTo(repoName)
            .and(TDdcBlob::blobId.name).inValues(blobIds)
        val update = Update().addToSet(TDdcBlob::references.name, "ref/$bucket/$refKey")
        updateMulti(Query(criteria), update)
    }

    fun removeRefFromBlob(projectId: String, repoName: String, bucket: String, refKey: String) {
        // 从blob ref列表中移除ref
        val criteria = Criteria
            .where(TDdcBlob::projectId.name).isEqualTo(projectId)
            .and(TDdcBlob::repoName.name).isEqualTo(repoName)
            .and(TDdcBlob::references.name).inValues("ref/${bucket}/${refKey}")
        val update = Update().pull(TDdcBlob::references.name, refKey)
        updateMulti(Query(criteria), update)
    }
}
