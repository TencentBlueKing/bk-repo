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

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.ddc.model.TDdcRefBase
import com.tencent.bkrepo.ddc.pojo.RefId
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime

abstract class RefBaseRepository<E : TDdcRefBase> : SimpleMongoDao<E>() {
    fun updateLastAccess(refId: RefId, lastAccessDate: LocalDateTime): UpdateResult {
        val criteria = refIdCriteria(refId.projectId, refId.repoName, refId.bucket, refId.key)
        val update = Update.update(TDdcRefBase::lastAccessDate.name, lastAccessDate)
        return updateFirst(Query(criteria), update)
    }

    fun createIfNotExists(ref: TDdcRefBase): E? {
        return try {
            insert(ref as E)
        } catch (e: DuplicateKeyException) {
            val criteria = refIdCriteria(ref.projectId, ref.repoName, ref.bucket, ref.key)
            val query = Query(criteria)
            findOne(query)
        }
    }

    fun delete(projectId: String, repoName: String, bucket: String, key: String): DeleteResult {
        val criteria = refIdCriteria(projectId, repoName, bucket, key)
        return remove(Query(criteria))
    }

    private fun refIdCriteria(projectId: String, repoName: String, bucket: String, key: String): Criteria =
        TDdcRefBase::projectId.isEqualTo(projectId)
            .and(TDdcRefBase::repoName.name).isEqualTo(repoName)
            .and(TDdcRefBase::bucket.name).isEqualTo(bucket)
            .and(TDdcRefBase::key.name).isEqualTo(key)
}
