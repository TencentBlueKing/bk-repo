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

package com.tencent.bkrepo.opdata.util

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.enums.ProjectType
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Component
class MetricsCacheUtil(
    projectMetricsRepository: ProjectMetricsRepository,
    projectModel: ProjectModel
) {

    init {
        Companion.projectMetricsRepository = projectMetricsRepository
        Companion.projectModel = projectModel
    }

    companion object {
        private lateinit var projectMetricsRepository: ProjectMetricsRepository
        private lateinit var projectModel: ProjectModel
        private val metricsCache: LoadingCache<String, List<TProjectMetrics>> by lazy {
            val cacheLoader = object : CacheLoader<String, List<TProjectMetrics>>() {
                override fun load(key: String): List<TProjectMetrics> {
                    val date = LocalDateTime.parse(key, DateTimeFormatter.ISO_DATE_TIME)
                    return projectMetricsRepository.findAllByCreatedDate(date)
                }
            }
            CacheBuilder.newBuilder()
                .maximumSize(5)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build(cacheLoader)
        }
        private val projectNumCache: LoadingCache<String, Long> by lazy {
            val cacheLoader = object : CacheLoader<String, Long>() {
                override fun load(key: String): Long {
                    return projectModel.getProjectNum(ProjectType.valueOf(key))
                }
            }
            CacheBuilder.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build(cacheLoader)
        }



        fun getProjectMetrics(dateStr: String): List<TProjectMetrics> {
            return metricsCache.get(dateStr)
        }

        fun gerProjectNodeNum(projectType: String): Long {
            return projectNumCache.get(projectType)
        }
    }
}