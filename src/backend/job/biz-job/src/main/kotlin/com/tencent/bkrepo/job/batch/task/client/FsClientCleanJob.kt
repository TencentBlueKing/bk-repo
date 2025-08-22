/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch.task.client

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.FsClientCleanJobProperties
import com.tencent.bkrepo.job.pojo.client.Client
import com.tencent.bkrepo.job.pojo.client.DailyClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Component
class FsClientCleanJob(
    private val properties: FsClientCleanJobProperties,
) : DefaultContextMongoDbJob<Client>(properties) {
    override fun collectionNames(): List<String> {
        return listOf(CLIENT_COLLECTION)
    }

    override fun buildQuery(): Query {
        return Query(
            where(Client::online).isEqualTo(false)
                .and(Client::heartbeatTime).lt(LocalDateTime.now().minusDays(properties.reserveDays))
        )
    }

    override fun mapToEntity(row: Map<String, Any?>): Client {
        val heartbeatTime = TimeUtils.parseMongoDateTimeStr(row[Client::heartbeatTime.name].toString())
            ?: LocalDateTime.MAX
        if (heartbeatTime == LocalDateTime.MAX) {
            logger.error("parse heartbeatTime error: ${row[Client::heartbeatTime.name].toString()}")
        }
        return Client(
            id = row[ID].toString(),
            projectId = row[Client::projectId.name].toString(),
            repoName = row[Client::repoName.name].toString(),
            mountPoint = row[Client::mountPoint.name].toString(),
            userId = row[Client::userId.name].toString(),
            ip = row[Client::ip.name].toString(),
            version = row[Client::version.name].toString(),
            os = row[Client::os.name].toString(),
            arch = row[Client::arch.name].toString(),
            online = row[Client::online.name].toString().toBoolean(),
            heartbeatTime = heartbeatTime
        )
    }

    override fun entityClass(): KClass<Client> {
        return Client::class
    }

    override fun run(row: Client, collectionName: String, context: JobContext) {
        context.total.incrementAndGet()
        logger.info("delete client: $row")
        val clientQuery = Query(Criteria.where(ID).isEqualTo(row.id))
        val result = mongoTemplate.remove(clientQuery, CLIENT_COLLECTION)
        if (result.deletedCount == 0L) {
            context.failed.incrementAndGet()
            logger.error("remove client failed: $row")
            return
        }
        val dailyClientQuery = Query(
            where(DailyClient::projectId).isEqualTo(row.projectId)
                .and(DailyClient::repoName).isEqualTo(row.repoName)
                .and(DailyClient::mountPoint).isEqualTo(row.mountPoint)
                .and(DailyClient::ip).isEqualTo(row.ip)
        )
        mongoTemplate.remove(dailyClientQuery, DAILY_CLIENT_COLLECTION)
        context.success.incrementAndGet()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FsClientCleanJob::class.java)
        private const val CLIENT_COLLECTION = "client"
        private const val DAILY_CLIENT_COLLECTION = "daily_client"
    }
}
