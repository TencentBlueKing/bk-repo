/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.separation.dao.repo

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.sharding.MonthRangeShardingMongoDao
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.job.separation.model.TSeparationNode
import com.tencent.bkrepo.job.separation.model.repo.TSeparationMavenMetadataRecord
import com.tencent.bkrepo.job.separation.pojo.query.MavenMetadata
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SeparationMavenMetadataDao : MonthRangeShardingMongoDao<TSeparationMavenMetadataRecord>() {

    fun upsertMetaData(
        mavenMetadata: MavenMetadata, separationDate: LocalDateTime
    ): UpdateResult {
        val criteria = Criteria.where(TSeparationMavenMetadataRecord::projectId.name).isEqualTo(mavenMetadata.projectId)
            .and(TSeparationMavenMetadataRecord::repoName.name).isEqualTo(mavenMetadata.repoName)
            .and(TSeparationMavenMetadataRecord::groupId.name).isEqualTo(mavenMetadata.groupId)
            .and(TSeparationMavenMetadataRecord::artifactId.name).isEqualTo(mavenMetadata.artifactId)
            .and(TSeparationMavenMetadataRecord::version.name).isEqualTo(mavenMetadata.version)
            .and(TSeparationMavenMetadataRecord::classifier.name).isEqualTo(mavenMetadata.classifier)
            .and(TSeparationMavenMetadataRecord::extension.name).isEqualTo(mavenMetadata.extension)
            .and(TSeparationMavenMetadataRecord::separationDate.name).isEqualTo(separationDate)

        val metadataQuery = Query(criteria)
        val update = Update().set(TSeparationMavenMetadataRecord::buildNo.name, mavenMetadata.buildNo)
            .set(TSeparationMavenMetadataRecord::timestamp.name, mavenMetadata.timestamp)
        return this.upsert(metadataQuery, update)
    }

    fun search(
        projectId: String, repoName: String,
        groupId: String, artifactId: String,
        version: String, separationDate: LocalDateTime
    ): List<TSeparationMavenMetadataRecord> {
        val pageRequest = Pages.ofRequest(0, 10000)
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = Criteria.where(TSeparationMavenMetadataRecord::projectId.name).isEqualTo(projectId)
            .and(TSeparationMavenMetadataRecord::repoName.name).isEqualTo(repoName)
            .and(TSeparationMavenMetadataRecord::groupId.name).isEqualTo(groupId)
            .and(TSeparationMavenMetadataRecord::artifactId.name).isEqualTo(artifactId)
            .and(TSeparationMavenMetadataRecord::separationDate.name).gte(startOfDay).lt(endOfDay)
            .and(TSeparationMavenMetadataRecord::version.name).isEqualTo(version)
        val metadataQuery = Query(criteria).with(pageRequest)
        return this.find(metadataQuery)
    }

    fun deleteById(id: String, separationDate: LocalDateTime): DeleteResult {
        val deleteQuery = Query(
            Criteria.where(ID).isEqualTo(id)
                .and(TSeparationMavenMetadataRecord::separationDate.name).isEqualTo(separationDate)
        )
        return this.remove(deleteQuery)
    }
}