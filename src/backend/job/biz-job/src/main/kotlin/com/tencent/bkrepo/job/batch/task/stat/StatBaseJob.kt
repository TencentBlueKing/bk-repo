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

package com.tencent.bkrepo.job.batch.task.stat

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.MongodbJobProperties
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.system.measureNanoTime

open class StatBaseJob(
    private val mongoTemplate: MongoTemplate,
    private val properties: MongodbJobProperties,
): DefaultContextJob(properties) {

    fun queryNodes(
        projectId: String,
        collection: String,
        context: JobContext,
        runAllRepo: Boolean,
        specialRepos: List<String>,
        batchSize: Int
    ){
        measureNanoTime {
            val criteria = Criteria.where(PROJECT).isEqualTo(projectId)
                .and(DELETED_DATE).isEqualTo(null)
            if (!runAllRepo && !specialRepoRunCheck() && specialRepos.isNotEmpty()) {
                criteria.andOperator(Criteria().and(REPO).nin(specialRepos))
            }
            val query = Query.query(criteria)
            var querySize: Int
            var lastId = ObjectId(MIN_OBJECT_ID)
            do {
                val newQuery = Query.of(query)
                    .addCriteria(Criteria.where(ID).gt(lastId))
                    .limit(batchSize)
                    .with(Sort.by(ID).ascending())
                val data = mongoTemplate.find<ActiveProjectEmptyFolderCleanupJob.Node>(
                    newQuery,
                    collection,
                )
                if (data.isEmpty()) {
                    break
                }
                data.forEach { runRow(it, context) }
                querySize = data.size
                lastId = data.last().id as ObjectId
            } while (querySize == batchSize)
        }.apply {
            val elapsedTime = HumanReadable.time(this)
            deleteEmptyFolders(collection, projectId, context)
            logger.info("project $projectId run completed, elapse $elapsedTime")
        }
    }



    // 特殊仓库每周统计一次
    private fun specialRepoRunCheck(): Boolean {
        val runDay = if (properties.specialDay < 1 || properties.specialDay > 7) {
            6
        } else {
            properties.specialDay
        }
        return DayOfWeek.of(runDay) == LocalDateTime.now().dayOfWeek
    }

    override fun doStart0(jobContext: JobContext) {
        TODO("Not yet implemented")
    }

}