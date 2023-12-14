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

package com.tencent.bkrepo.common.operate.service.dao

import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.operate.api.pojo.ProjectUsageStatisticsListOption
import com.tencent.bkrepo.common.operate.service.model.TProjectUsageStatistics
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class ProjectUsageStatisticsDao : SimpleMongoDao<TProjectUsageStatistics>() {
    fun inc(
        projectId: String,
        reqCount: Long,
        receivedBytes: Long,
        responseBytes: Long,
        start: Long,
    ) {
        val criteria = TProjectUsageStatistics::start.isEqualTo(start)
            .and(TProjectUsageStatistics::projectId.name).isEqualTo(projectId)
        var needToUpdate = false
        val update = Update()
        if (reqCount != 0L) {
            update.inc(TProjectUsageStatistics::reqCount.name, reqCount)
            needToUpdate = true
        }

        if (receivedBytes != 0L) {
            update.inc(TProjectUsageStatistics::receiveBytes.name, receivedBytes)
            needToUpdate = true
        }

        if (responseBytes != 0L) {
            update.inc(TProjectUsageStatistics::responseByte.name, responseBytes)
            needToUpdate = true
        }

        if (needToUpdate) {
            upsert(Query(criteria), update)
        }
    }

    fun find(options: ProjectUsageStatisticsListOption): Page<TProjectUsageStatistics> {
        with(options) {
            val criteria = Criteria()
            projectId?.let { criteria.and(TProjectUsageStatistics::projectId.name).isEqualTo(it) }
            start?.let { criteria.and(TProjectUsageStatistics::start.name).gte(it) }
            end?.let { criteria.and(TProjectUsageStatistics::start.name).lt(it) }
            val query = Query(criteria)
            val total = count(query)
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            query.with(pageRequest)
            val records = find(query)
            return Pages.ofResponse(pageRequest, total, records)
        }
    }

    fun delete(start: Long?, end: Long): DeleteResult {
        val criteria = Criteria()
        start?.let { criteria.and(TProjectUsageStatistics::start.name).gte(it) }
        criteria.and(TProjectUsageStatistics::start.name).lt(end)
        return remove(Query(criteria))
    }
}
