/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.handler.impl

import com.tencent.bkrepo.common.metadata.service.project.ProjectUsageStatisticsService
import com.tencent.bkrepo.opdata.constant.DURATION
import com.tencent.bkrepo.opdata.constant.OPDATA_GRAFANA_NUMBER
import com.tencent.bkrepo.opdata.constant.OPDATA_GRAFANA_STRING
import com.tencent.bkrepo.opdata.handler.QueryHandler
import com.tencent.bkrepo.opdata.pojo.Columns
import com.tencent.bkrepo.opdata.pojo.QueryResult
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class ProjectTrafficHandler(
    private val projectUsageStatisticsService: ProjectUsageStatisticsService,
) : QueryHandler {
    override val metric: Metrics = Metrics.PROJECTTRAFFIC
    override fun handle(target: Target, result: MutableList<Any>) {
        val reqData = if (target.data is Map<*, *>) {
            target.data as Map<String, Any>
        } else {
            null
        }
        val duration = reqData?.get(DURATION)?.toString()?.toLongOrNull() ?: 1
        val rows = ArrayList<List<Any>>()
        val start = LocalDate.now().minusDays(duration - 1).atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        projectUsageStatisticsService.sum(start).forEach { (projectId, statistics) ->
            rows.add(
                listOf(
                    projectId,
                    statistics.receiveBytes,
                    statistics.responseBytes
                ),
            )
        }
        val columns = ArrayList<Columns>()
        columns.add(Columns("projectId", OPDATA_GRAFANA_STRING))
        columns.add(Columns("receive", OPDATA_GRAFANA_NUMBER))
        columns.add(Columns("response", OPDATA_GRAFANA_NUMBER))
        val data = QueryResult(columns, rows, target.type)
        result.add(data)
    }
}
