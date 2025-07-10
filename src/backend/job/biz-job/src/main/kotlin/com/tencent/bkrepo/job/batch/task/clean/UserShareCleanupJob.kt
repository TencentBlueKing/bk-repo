/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.clean

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.UserShareCleanupJobProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 清理用户分享记录
 */
@Component
@EnableConfigurationProperties(UserShareCleanupJobProperties::class)
class UserShareCleanupJob(
    private val properties: UserShareCleanupJobProperties,
) : DefaultContextMongoDbJob<UserShareCleanupJob.UserShareRecord>(properties) {

    data class UserShareRecord(
        val id: String,
        val expiredDate: LocalDateTime? = null,
        val permits: Int? = null,
        val lastModifiedDate: LocalDateTime,
    )

    data class UserShareApproval(
        val shareId: String
    )

    override fun collectionNames(): List<String> {
        return listOf(USER_SHARE_RECORD_COLLECTION)
    }

    override fun buildQuery(): Query {
        return Query()
    }

    override fun mapToEntity(row: Map<String, Any?>): UserShareRecord {
        return UserShareRecord(
            id = row["id"] as String,
            expiredDate = row["expiredDate"]?.let { TimeUtils.parseMongoDateTimeStr(it.toString()) },
            permits = row["permits"]?.toString()?.toInt(),
            lastModifiedDate = TimeUtils.parseMongoDateTimeStr(row["lastModifiedDate"].toString())
                ?: LocalDateTime.now(),
        )
    }

    override fun entityClass(): KClass<UserShareRecord> {
        return UserShareRecord::class
    }

    override fun run(row: UserShareRecord, collectionName: String, context: JobContext) {
        if (row.expiredDate != null) {
            expiredRecord(row)?.let {
                return delete(it)
            }
        }
        if (row.permits != null) {
            noPermitsRecord(row)?.let {
                return delete(it)
            }
        }
    }

    private fun delete(id: String) {
        val approvalQuery = Query(Criteria.where(UserShareApproval::shareId.name).isEqualTo(id))
        mongoTemplate.remove(approvalQuery, USER_SHARE_APPROVAL_COLLECTION)
        val recordQuery = Query(Criteria.where(ID).isEqualTo(id))
        mongoTemplate.remove(recordQuery, USER_SHARE_RECORD_COLLECTION)
    }

    /**
     * 可用次数为0，且最后修改时间距任务执行时超过保留时间
     */
    private fun noPermitsRecord(row: UserShareRecord): String? {
        if (row.permits!! > 0 && row.lastModifiedDate.plusDays(properties.reserveDays).isAfter(LocalDateTime.now())) {
            return null
        }
        return row.id
    }

    /**
     * 过期且超过保留时间
     */
    private fun expiredRecord(row: UserShareRecord): String? {
        if (row.expiredDate!!.plusDays(properties.reserveDays).isAfter(LocalDateTime.now())) {
            return null
        }
        return row.id
    }

    companion object {
        private const val USER_SHARE_RECORD_COLLECTION = "user_share_record"
        private const val USER_SHARE_APPROVAL_COLLECTION = "user_share_approval"
    }
}
