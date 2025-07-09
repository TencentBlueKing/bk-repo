/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.helm.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.helm.model.THelmChartEventRecord
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.UpdateDefinition
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import java.time.LocalDateTime


@Repository
class HelmChartEventRecordDao : SimpleMongoDao<THelmChartEventRecord>() {

    fun updateEventTimeByProjectIdAndRepoName(
        projectId: String, repoName: String, eventTime: LocalDateTime
    ) {
        val update = Update().set(THelmChartEventRecord::eventTime.name, eventTime)
        update(projectId, repoName, update)
    }

    fun updateIndexRefreshTimeByProjectIdAndRepoName(
        projectId: String, repoName: String, indexRefreshTime: LocalDateTime
    ) {
        val update = Update().set(THelmChartEventRecord::indexRefreshTime.name, indexRefreshTime)
        update(projectId, repoName, update)
    }

    private fun update(projectId: String, repoName: String, update: UpdateDefinition) {
        val criteria = where(THelmChartEventRecord::projectId).isEqualTo(projectId)
            .and(THelmChartEventRecord::repoName.name).isEqualTo(repoName)
        val query = Query(criteria)
        determineMongoTemplate().upsert(query, update, THelmChartEventRecord::class.java)
    }

    fun checkIndexExpiredStatus(projectId: String, repoName: String): Boolean {
        val criteria = where(THelmChartEventRecord::projectId).isEqualTo(projectId)
            .and(THelmChartEventRecord::repoName.name).isEqualTo(repoName)
        val record = filterRecords(criteria)
        return record.isNotEmpty()
    }

    fun findAllRecordsNeedToRefresh(): List<THelmChartEventRecord> {
        return filterRecords()
    }


    private fun filterRecords(criteria: Criteria? = null): List<THelmChartEventRecord> {
        val query = if (criteria == null) {
            Query()
        } else {
            Query(criteria)
        }

        var records = determineMongoTemplate().find(query, THelmChartEventRecord::class.java)

        if (!records.isNullOrEmpty()) {
            records = records.filter { it.indexRefreshTime == null || it.eventTime.isAfter(it.indexRefreshTime)  }
        }
        return records
    }
}