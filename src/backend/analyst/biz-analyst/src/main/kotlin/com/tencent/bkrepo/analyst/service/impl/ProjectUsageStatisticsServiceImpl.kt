/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.pojo.project.ProjectUsageStatistics
import com.tencent.bkrepo.common.metadata.pojo.project.ProjectUsageStatisticsListOption
import com.tencent.bkrepo.common.metadata.service.project.ProjectUsageStatisticsService
import org.springframework.scheduling.annotation.Async

open class ProjectUsageStatisticsServiceImpl(
    private val projectUsageStatisticsService: ProjectUsageStatisticsService
) : ProjectUsageStatisticsService {

    @Async
    override fun inc(projectId: String, reqCount: Long, receivedBytes: Long, responseBytes: Long) {
        projectUsageStatisticsService.inc(projectId, reqCount, receivedBytes, responseBytes)
    }

    override fun page(options: ProjectUsageStatisticsListOption): Page<ProjectUsageStatistics> {
        throw UnsupportedOperationException()
    }

    override fun delete(start: Long?, end: Long) {
        throw UnsupportedOperationException()
    }

    override fun sum(start: Long, end: Long): Map<String, ProjectUsageStatistics> {
        throw UnsupportedOperationException()
    }
}
