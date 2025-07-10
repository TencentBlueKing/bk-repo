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

import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.FsClientOfflineProperties
import com.tencent.bkrepo.job.pojo.client.Client
import com.tencent.bkrepo.job.pojo.client.DailyClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@EnableConfigurationProperties(FsClientOfflineProperties::class)
class FsClientOfflineJob(
    properties: FsClientOfflineProperties,
    private val mongoTemplate: MongoTemplate
): DefaultContextJob(properties) {
    override fun doStart0(jobContext: JobContext) {
        val batchSize = 1000
        val query = Query(
            Criteria.where(Client::online.name).isEqualTo(true)
                .and(Client::heartbeatTime.name).lt(LocalDateTime.now().minusMinutes(2))
        ).limit(batchSize)
        val update = Update.update(Client::online.name, false)

        while (true) {
            val clients = mongoTemplate.find(query, Client::class.java, COLLECTION)
            for (client in clients) {
                val dailyClient = DailyClient(
                    projectId = client.projectId,
                    repoName = client.repoName,
                    mountPoint = client.mountPoint,
                    userId = client.userId,
                    ip = client.ip,
                    version = client.version,
                    os = client.os,
                    arch = client.arch,
                    action = "finish",
                    time = LocalDateTime.now()
                )
                mongoTemplate.insert(dailyClient, DAILY_COLLECTION)
            }
            val result = mongoTemplate.updateMulti(query, update, COLLECTION)
            logger.info("${result.modifiedCount} client(s) change to offline")
            jobContext.total.addAndGet(result.matchedCount)
            jobContext.success.addAndGet(result.modifiedCount)
            if (result.modifiedCount < batchSize) {
                return
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FsClientOfflineJob::class.java)
        private const val COLLECTION = "client"
        private const val DAILY_COLLECTION = "daily_client"
    }
}
