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

package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.job.IGNORE_PROJECT_PREFIX_LIST
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Component
class ActiveProjectService(
    private val mongoTemplate: MongoTemplate
) {
    private var activeProjects = mutableSetOf<String>()

    private var downloadActiveProjects = mutableSetOf<String>()

    private var uploadActiveProjects = mutableSetOf<String>()


    fun getActiveProjects(): MutableSet<String> {
        return activeProjects
    }

    fun getDownloadActiveProjects(): MutableSet<String> {
        return downloadActiveProjects
    }

    fun getUploadActiveProjects(): MutableSet<String> {
        return uploadActiveProjects
    }


    private fun getActiveProjectsFromOpLog() {
        downloadActiveProjects = getActiveProjects(DOWNLOAD_EVENTS)
        uploadActiveProjects = getActiveProjects(UPLOAD_EVENTS)
        activeProjects = getActiveProjects()
    }

    private fun getActiveProjects(types: List<String> = emptyList()): MutableSet<String> {
        val tempList = mutableSetOf<String>()
        val months = listOf(
            LocalDate.now().format(DateTimeFormatter.ofPattern(YEAR_MONTH_FORMAT)),
            LocalDate.now().minusMonths(1)
                .format(DateTimeFormatter.ofPattern(YEAR_MONTH_FORMAT))
        )

        months.forEach {
            val collectionName = COLLECTION_NAME_PREFIX +it
            val criteria = Criteria().andOperator(
                IGNORE_PROJECT_PREFIX_LIST.map { where(ProjectInfo::name).not().regex(it) }
            )
            if (types.isNotEmpty()) {
                criteria.and(TYPE).`in`(types)
            }
            val query = Query(criteria)
            val data = mongoTemplate.findDistinct(query, PROJECT, collectionName, String::class.java)
            tempList.addAll(data)
        }
        return tempList
    }

    /**
     * 定时从db中读取数据更新缓存
     */
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = INITIAL_DELAY, timeUnit = TimeUnit.SECONDS)
    fun refreshActiveProjects() {
        getActiveProjectsFromOpLog()
    }

    companion object {
        private const val INITIAL_DELAY = 2L
        private const val FIXED_DELAY = 60L
        private const val COLLECTION_NAME_PREFIX = "artifact_oplog_"
        private const val YEAR_MONTH_FORMAT = "yyyyMM"
        private val DOWNLOAD_EVENTS = listOf(
            EventType.NODE_DOWNLOADED.name, EventType.VERSION_DOWNLOAD.name
        )
        private val UPLOAD_EVENTS = listOf(
            EventType.NODE_CREATED.name, EventType.VERSION_CREATED.name
        )
    }
}

