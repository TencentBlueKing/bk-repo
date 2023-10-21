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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.opdata.constant.TO_GIGABYTE
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.ProjectMetrics
import com.tencent.bkrepo.opdata.pojo.ProjectMetricsOption
import com.tencent.bkrepo.opdata.pojo.ProjectMetricsRequest
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import org.springframework.stereotype.Service

@Service
class ProjectMetricsService (
    private val projectMetricsRepository: ProjectMetricsRepository
    ){

    fun page(option: ProjectMetricsOption): Page<TProjectMetrics> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val queryResult = if (!projectId.isNullOrEmpty()) {
                projectMetricsRepository.findByProjectIdAndCreatedDateOrderByCreatedDateDesc(
                    projectId!!, createdDate, pageRequest
                )
            } else {
                projectMetricsRepository.findByCreatedDateOrderByCreatedDateDesc(createdDate, pageRequest)
            }
            queryResult.content.forEach {
                it.capSize = it.capSize / TO_GIGABYTE
                it.repoMetrics.forEach {  repo ->
                    repo.size = repo.size / TO_GIGABYTE
                }
            }
            return Pages.ofResponse(pageRequest, queryResult.totalElements, queryResult.content)
        }
    }

    fun list(metricsRequest: ProjectMetricsRequest): List<ProjectMetrics> {
        val queryResult = if (metricsRequest.oldDataFlag) {
            projectMetricsRepository.findAllByCreatedDateNot()
        } else {
            projectMetricsRepository.findAllByCreatedDateAfter()
        }
        val result = mutableListOf<ProjectMetrics>()
        queryResult.map {
                if (it.capSize >= metricsRequest.limitSize) {
                    result.add(ProjectMetrics(
                        projectId = it.projectId,
                        capSize = it.capSize / TO_GIGABYTE,
                        nodeNum = it.nodeNum,
                        createdDate = it.createdDate
                    ))
                }
            }
        return result
    }
}