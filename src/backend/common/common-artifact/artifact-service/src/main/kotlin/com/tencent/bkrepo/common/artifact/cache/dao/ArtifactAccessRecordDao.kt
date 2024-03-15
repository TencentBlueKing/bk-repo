/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.cache.dao

import com.tencent.bkrepo.common.artifact.cache.model.TArtifactAccessRecord
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneId

@Repository
class ArtifactAccessRecordDao : SimpleMongoDao<TArtifactAccessRecord>() {
    fun find(projectId: String, repoName: String, fullPath: String, sha256: String): TArtifactAccessRecord? {
        return findOne(Query(buildCriteria(projectId, repoName, fullPath, sha256)))
    }

    fun update(
        projectId: String,
        repoName: String,
        fullPath: String,
        sha256: String,
        cacheMissCount: Long = 1L,
    ) {
        val now = LocalDateTime.now()
        val update = Update()
        update.set(TArtifactAccessRecord::lastModifiedDate.name, now)
        if (cacheMissCount > 0) {
            update.inc(TArtifactAccessRecord::cacheMissCount.name, cacheMissCount)
        }
        update.push(TArtifactAccessRecord::accessTimeSequence.name, now.atZone(ZoneId.systemDefault()).toEpochSecond())
        val query = Query(buildCriteria(projectId, repoName, fullPath, sha256))
        updateFirst(query, update)
    }

    private fun buildCriteria(projectId: String, repoName: String, fullPath: String, sha256: String): Criteria {
        return TArtifactAccessRecord::projectId.isEqualTo(projectId)
            .and(TArtifactAccessRecord::repoName.name).isEqualTo(repoName)
            .and(TArtifactAccessRecord::fullPath.name).isEqualTo(fullPath)
            .and(TArtifactAccessRecord::sha256.name).isEqualTo(sha256)
    }
}
