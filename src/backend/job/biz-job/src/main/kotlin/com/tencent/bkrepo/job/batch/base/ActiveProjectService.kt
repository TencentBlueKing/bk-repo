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
import com.tencent.bkrepo.common.operate.service.model.TOperateLog
import com.tencent.bkrepo.job.IGNORE_PROJECT_PREFIX_LIST
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.ne
import org.springframework.data.mongodb.core.query.not
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

    private var activeUsers = mutableSetOf<String>()

    fun getActiveProjects(): MutableSet<String> {
        return activeProjects
    }

    fun getDownloadActiveProjects(): MutableSet<String> {
        return downloadActiveProjects
    }

    fun getUploadActiveProjects(): MutableSet<String> {
        return uploadActiveProjects
    }

    fun getActiveUsers(): Set<String> {
        return activeUsers
    }

    private fun findDistinct(field: String, criteria: Criteria): MutableSet<String> {
        val tempList = HashSet<String>()
        val months = listOf(
            LocalDate.now().format(DateTimeFormatter.ofPattern(YEAR_MONTH_FORMAT)),
            LocalDate.now().minusMonths(1)
                .format(DateTimeFormatter.ofPattern(YEAR_MONTH_FORMAT))
        )

        months.forEach {
            val collectionName = COLLECTION_NAME_PREFIX + it
            val query = Query(criteria)
            val data = mongoTemplate.findDistinct(query, field, collectionName, String::class.java)
            tempList.addAll(data)
        }
        return tempList
    }

    /**
     * 定时从db中读取数据更新缓存
     */
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = INITIAL_DELAY, timeUnit = TimeUnit.MINUTES)
    fun refreshActiveProjects() {
        val criteriaList = IGNORE_PROJECT_PREFIX_LIST.mapTo(ArrayList()) { prefix ->
            TOperateLog::projectId.not().regex("^$prefix")
        }
        criteriaList.add(TOperateLog::projectId.ne(""))
        fun buildTypesCriteriaList(types: List<String>): List<Criteria> {
            return ArrayList(criteriaList).apply { add(TOperateLog::type.inValues(types)) }
        }

        // all
        activeProjects = findDistinct(TOperateLog::projectId.name, Criteria().andOperator(criteriaList))

        // download event
        downloadActiveProjects = findDistinct(
            TOperateLog::projectId.name,
            Criteria().andOperator(buildTypesCriteriaList(DOWNLOAD_EVENTS))
        )

        // upload event
        uploadActiveProjects = findDistinct(
            TOperateLog::projectId.name,
            Criteria().andOperator(buildTypesCriteriaList(UPLOAD_EVENTS))
        )

        // active users
        activeUsers = findDistinct(TOperateLog::userId.name, TOperateLog::userId.ne(""))
        logger.info("refresh active projects and users success")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ActiveProjectService::class.java)
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

