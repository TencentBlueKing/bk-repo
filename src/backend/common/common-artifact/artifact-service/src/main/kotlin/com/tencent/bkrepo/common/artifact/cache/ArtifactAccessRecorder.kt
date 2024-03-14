/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.cache

import com.tencent.bkrepo.common.artifact.cache.dao.ArtifactAccessRecordDao
import com.tencent.bkrepo.common.artifact.cache.model.TArtifactAccessRecord
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 记录制品访问时间，用于统计项目制品使用习惯
 */
@Component
class ArtifactAccessRecorder(
    private val preloadProperties: PreloadProperties,
    private val artifactAccessRecordDao: ArtifactAccessRecordDao
) {
    fun onArtifactAccess(node: NodeDetail, cacheMiss: Boolean) {
        if (preloadProperties.onlyRecordCacheMiss && !cacheMiss || node.folder) {
            return
        }

        with(node) {
            val criteria = TArtifactAccessRecord::projectId.isEqualTo(projectId)
                .and(TArtifactAccessRecord::repoName.name).isEqualTo(repoName)
                .and(TArtifactAccessRecord::fullPath.name).isEqualTo(fullPath)
                .and(TArtifactAccessRecord::sha256.name).isEqualTo(sha256)
            val record = artifactAccessRecordDao.findOne(Query(criteria))
            val now = LocalDateTime.now()

            // 短时间内多次访问时只记录一次
            val accessInterval = record?.accessTimeSequence?.maxOf { it }.let { Duration.between(it, now) }
            if (accessInterval != null && accessInterval > preloadProperties.minAccessInterval) {
                return
            }

            // try insert
            if (record == null) {
                try {
                    artifactAccessRecordDao.insert(
                        TArtifactAccessRecord(
                            createdDate = now,
                            lastModifiedDate = now,
                            projectId = projectId,
                            repoName = repoName,
                            fullPath = fullPath,
                            sha256 = sha256!!,
                            cacheMissCount = if (cacheMiss) 1L else 0L,
                            nodeCreateTime = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME),
                            accessTimeSequence = listOf(now)
                        )
                    )
                } catch (e: DuplicateKeyException) {
                    logger.warn("insert access record failed, try to update it", e)
                }
            }

            // update
            val update = Update()
            update.set(TArtifactAccessRecord::lastModifiedDate.name, now)
            if (cacheMiss) {
                update.inc(TArtifactAccessRecord::cacheMissCount.name, 1)
            }
            update.push(TArtifactAccessRecord::accessTimeSequence.name, now)
            artifactAccessRecordDao.updateFirst(Query(criteria), update)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactAccessRecorder::class.java)
    }
}
