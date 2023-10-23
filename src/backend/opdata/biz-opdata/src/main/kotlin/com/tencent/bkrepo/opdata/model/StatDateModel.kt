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
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class StatDateModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate
) {

    companion object {
        private val JOB_NAME = "NodeStatCompositeMongoDbBatchJob"
    }

    fun getShedLockInfo(id: String = JOB_NAME): LocalDateTime {
        val query = Query(Criteria.where(ID).isEqualTo(id))
        val result = mongoTemplate.find(query, ShedlockInfo::class.java, SHED_LOCK_COLLECTION_NAME)
        return if (result.isEmpty()) {
            LocalDate.now().minusDays(1).atStartOfDay()
        } else {
            getLockedAtDate(result.first())
        }
    }


    private fun getLockedAtDate(lockInfo: ShedlockInfo): LocalDateTime {
        with(lockInfo) {
            return if (lockUntil!!.isBefore(LocalDateTime.now())) {
                lockedAt!!.toLocalDate().atStartOfDay()
            } else {
                lockedAt!!.toLocalDate().minusDays(1).atStartOfDay()
            }
        }
    }

    data class ShedlockInfo(
        val id: String,
        var lockUntil: LocalDateTime?,
        var lockedAt: LocalDateTime?,
        var lockedBy: String,
    )
}
