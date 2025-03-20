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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.metadata.model.TOperateLog
import com.tencent.bkrepo.job.IGNORE_PROJECT_PREFIX_LIST
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.ne
import org.springframework.data.mongodb.core.query.not
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class ActiveProjectService(
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private var lockOperation: LockOperation
) {

    private var activeProjects = mutableSetOf<String>()

    private var downloadActiveProjects = mutableSetOf<String>()

    private var uploadActiveProjects = mutableSetOf<String>()

    private var moveCopyActiveProjects = mutableSetOf<String>()


    private var activeUsers = mutableSetOf<String>()

    fun getActiveProjects(): MutableSet<String> {
        return getValue(ACTIVE_PROJECTS, activeProjects)
    }

    fun getDownloadActiveProjects(): MutableSet<String> {
        return getValue(DOWNLOAD_ACTIVE_PROJECTS, downloadActiveProjects)
    }

    fun getUploadActiveProjects(): MutableSet<String> {
        return getValue(UPLOAD_ACTIVE_PROJECTS, uploadActiveProjects)
    }

    fun getActiveUsers(): Set<String> {
        return getValue(ACTIVE_USERS, activeUsers)
    }

    fun getMoveCopyActiveProjects(): Set<String> {
        return getValue(MOVE_COPY_ACTIVE_PROJECTS, moveCopyActiveProjects)
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
    @Scheduled(cron = "0 0 1 * * ?")
    fun refreshActiveProjects() {
        logger.info("start to refresh active projects and users")
        // 当没有 redis 的场景下需要所有保存在所有机器内存中
        // 有 redis 的场景只允许获取到锁的机器上进行刷下即可
        var lock: Any? = null
        if (redisEnableCheck(buildRedisKey(ACTIVE_PROJECTS))) {
            lock = getLock()
            if (lock == null) {
                return
            }
        }
        refreshActiveData()
        removeLock(lock)
        logger.info("refresh active projects and users success")
    }

    private fun redisEnableCheck(key: String): Boolean {
        return try {
            redisTemplate.opsForValue().get(key)
            true
        }catch (e: Exception) {
            false
        }
    }

    fun getLock(): Any? {
        val key = ACTIVE_DATA_REFRESH_JOB
        val lock = lockOperation.getLock(key)
        return if (lockOperation.acquireLock(lockKey = key, lock = lock)) {
            logger.info("Lock for key $key has been acquired.")
            lock
        } else {
            null
        }
    }

    fun removeLock(lock: Any?) {
        lock?.let {
            lockOperation.close(ACTIVE_DATA_REFRESH_JOB, it)
        }
    }

    private fun refreshActiveData() {
        val criteriaList = IGNORE_PROJECT_PREFIX_LIST.mapTo(ArrayList()) { prefix ->
            TOperateLog::projectId.not().regex("^$prefix")
        }
        criteriaList.add(TOperateLog::projectId.ne(""))
        fun buildTypesCriteriaList(types: List<String>): List<Criteria> {
            return ArrayList(criteriaList).apply { add(TOperateLog::type.inValues(types)) }
        }
        moveCopyActiveProjects = findDistinct(
            TOperateLog::projectId.name,
            Criteria().andOperator(buildTypesCriteriaList(MOVE_COPY_EVENTS))
        )
        storeValue(MOVE_COPY_ACTIVE_PROJECTS, moveCopyActiveProjects)

        // download event
        downloadActiveProjects = findDistinct(
            TOperateLog::projectId.name,
            Criteria().andOperator(buildTypesCriteriaList(DOWNLOAD_EVENTS))
        )
        storeValue(DOWNLOAD_ACTIVE_PROJECTS, downloadActiveProjects)

        // upload event
        uploadActiveProjects = findDistinct(
            TOperateLog::projectId.name,
            Criteria().andOperator(buildTypesCriteriaList(UPLOAD_EVENTS))
        )
        storeValue(UPLOAD_ACTIVE_PROJECTS, uploadActiveProjects)


        activeProjects = downloadActiveProjects.union(uploadActiveProjects)
            .union(moveCopyActiveProjects).toMutableSet()
        storeValue(ACTIVE_PROJECTS, activeProjects)

        // active users
        activeUsers = findDistinct(TOperateLog::userId.name, TOperateLog::userId.ne(""))
        storeValue(ACTIVE_USERS, activeUsers)
    }

    private fun buildRedisKey(key: String): String {
        return JOB_KEY_PREFIX + key
    }

    private fun storeValue(key: String, value: Set<String>) {
        try {
            if (value.isEmpty()) {
                redisTemplate.opsForValue().set(buildRedisKey(key), StringPool.EMPTY)
            } else {
                redisTemplate.opsForValue().set(buildRedisKey(key), value.toJsonString())
            }
        } catch (e: Exception) {
            logger.warn("store active projects error: ${e.message}")
        }
    }

    private fun getValue(key: String, cacheValue: MutableSet<String>): MutableSet<String> {
        if (cacheValue.isNotEmpty()) return cacheValue
        try {
            val valueStr = redisTemplate.opsForValue().get(buildRedisKey(key))
            if (valueStr.isNullOrEmpty()) {
                return mutableSetOf()
            }
            return valueStr.readJsonString<Set<String>>().toMutableSet()
        } catch (e: Exception) {
            logger.warn("get active projects from redis error:${e.message}")
            return mutableSetOf()
        }
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
        private val MOVE_COPY_EVENTS = listOf(
            EventType.NODE_MOVED.name, EventType.NODE_COPIED.name
        )
        private const val JOB_KEY_PREFIX = "job:projectOrUser:"
        private const val ACTIVE_USERS = "activeUsers"
        private const val ACTIVE_PROJECTS = "activeProjects"
        private const val UPLOAD_ACTIVE_PROJECTS = "uploadActiveProjects"
        private const val DOWNLOAD_ACTIVE_PROJECTS = "downloadActiveProjects"
        private const val MOVE_COPY_ACTIVE_PROJECTS = "moveCopyActiveProjects"
        private const val ACTIVE_DATA_REFRESH_JOB = "activeDataRefreshJob"

    }
}

