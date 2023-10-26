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

package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.common.job.JobAutoConfiguration.Companion.SHED_LOCK_COLLECTION_NAME
import com.tencent.bkrepo.common.mongo.constant.ID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class StatDateModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate
) {

    companion object {
        private val JOB_NAMES = listOf("ProjectRepoMetricsStatJob")
    }

    fun getShedLockInfo(ids: List<String> = JOB_NAMES): LocalDateTime {
        return try {
            val query = Query(Criteria.where(ID).`in`(ids))
            val result = mongoTemplate.find(query, ShedlockInfo::class.java, SHED_LOCK_COLLECTION_NAME)
            getLockedAtDate(result)
        } catch (e: Exception) {
            LocalDate.now().minusDays(1).atStartOfDay()
        }
    }


    private fun getLockedAtDate(lockInfos: List<ShedlockInfo>): LocalDateTime {
        if (lockInfos.isEmpty()) {
            return LocalDate.now().minusDays(1).atStartOfDay()
        }
        var statDateTime: LocalDateTime? = null
        lockInfos.forEach {
            val tempStatDate = if (it.lockUntil!!.isBefore(LocalDateTime.now())) {
                it.lockedAt!!.toLocalDate().atStartOfDay()
            } else {
                it.lockedAt!!.toLocalDate().minusDays(1).atStartOfDay()
            }
            if (statDateTime == null) {
                statDateTime = tempStatDate
            } else {
                if (statDateTime!!.isBefore(tempStatDate)) {
                    statDateTime = tempStatDate
                }
            }
        }
        return statDateTime!!
    }

    data class ShedlockInfo(
        val id: String,
        var lockUntil: LocalDateTime?,
        var lockedAt: LocalDateTime?,
        var lockedBy: String,
    )
}
