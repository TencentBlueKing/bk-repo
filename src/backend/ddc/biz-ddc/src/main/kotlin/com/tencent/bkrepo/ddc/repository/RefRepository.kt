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

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.ddc.model.TDdcRef
import org.springframework.data.mongodb.core.FindAndReplaceOptions
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class RefRepository : SimpleMongoDao<TDdcRef>() {
    fun find(projectId: String, repoName: String, bucket: String, key: String, includePayload: Boolean): TDdcRef? {
        val criteria = TDdcRef::projectId.isEqualTo(projectId)
            .and(TDdcRef::repoName.name).isEqualTo(repoName)
            .and(TDdcRef::bucket.name).isEqualTo(bucket)
            .and(TDdcRef::key.name).isEqualTo(key)
        val query = Query(criteria)
        if(!includePayload) {
            query.fields().exclude(TDdcRef::inlineBlob.name)
        }
        return findOne(query)
    }

    fun replace(ref: TDdcRef): TDdcRef? {
        val criteria = TDdcRef::projectId.isEqualTo(ref.projectId)
            .and(TDdcRef::repoName.name).isEqualTo(ref.repoName)
            .and(TDdcRef::bucket.name).isEqualTo(ref.bucket)
            .and(TDdcRef::key.name).isEqualTo(ref.key)
        val query = Query(criteria)
        val options = FindAndReplaceOptions().upsert()
        return determineMongoTemplate().findAndReplace(query, ref, options)
    }

    fun finalize(projectId: String, repoName: String, bucket: String, key: String): UpdateResult {
        val criteria = TDdcRef::projectId.isEqualTo(projectId)
            .and(TDdcRef::repoName.name).isEqualTo(repoName)
            .and(TDdcRef::bucket.name).isEqualTo(bucket)
            .and(TDdcRef::key.name).isEqualTo(key)
        val update = Update.update(TDdcRef::finalized.name, true)
        return updateFirst(Query(criteria), update)
    }
}