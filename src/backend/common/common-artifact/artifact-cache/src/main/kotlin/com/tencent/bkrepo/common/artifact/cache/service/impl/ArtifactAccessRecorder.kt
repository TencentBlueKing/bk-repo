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

package com.tencent.bkrepo.common.artifact.cache.service.impl

import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.dao.ArtifactAccessRecordDao
import com.tencent.bkrepo.common.artifact.cache.model.TArtifactAccessRecord
import com.tencent.bkrepo.common.artifact.event.ArtifactResponseEvent
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream.Companion.METADATA_KEY_CACHE_ENABLED
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.dao.DuplicateKeyException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 记录制品访问时间，用于统计项目制品使用习惯
 */
class ArtifactAccessRecorder(
    private val preloadProperties: ArtifactPreloadProperties,
    private val artifactAccessRecordDao: ArtifactAccessRecordDao,
) {

    @Async
    fun onArtifactAccess(node: NodeDetail, cacheMiss: Boolean) {
        val validNode = !node.folder && node.size > preloadProperties.minSize.toBytes()
        val shouldNotRecord = preloadProperties.onlyRecordCacheMiss && !cacheMiss
        if (!preloadProperties.enabled || shouldNotRecord || !validNode) {
            return
        }

        with(node) {
            val record = artifactAccessRecordDao.find(projectId, repoName, fullPath, sha256!!)
            val now = LocalDateTime.now()
            val nowTimestamp = now.atZone(ZoneId.systemDefault()).toEpochSecond()

            // 短时间内多次访问时只记录一次
            val accessInterval = record?.accessTimeSequence
                ?.maxOf { it }
                ?.let { Duration.ofMillis(nowTimestamp - it) }
            if (accessInterval != null && accessInterval < preloadProperties.minAccessInterval) {
                return
            }

            val cacheMissCount = if (cacheMiss) 1L else 0L
            if (record == null) {
                try {
                    // try insert
                    artifactAccessRecordDao.insert(
                        TArtifactAccessRecord(
                            createdDate = now,
                            lastModifiedDate = now,
                            projectId = projectId,
                            repoName = repoName,
                            fullPath = fullPath,
                            sha256 = sha256!!,
                            cacheMissCount = cacheMissCount,
                            nodeCreateTime = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME),
                            accessTimeSequence = listOf(nowTimestamp)
                        )
                    )
                } catch (e: DuplicateKeyException) {
                    logger.warn("insert access record failed, try to update it", e)
                    artifactAccessRecordDao.update(projectId, repoName, fullPath, sha256!!, cacheMissCount)
                }
            } else {
                // update
                artifactAccessRecordDao.update(projectId, repoName, fullPath, sha256!!, cacheMissCount)
            }
        }
    }

    /**
     * 清理访问记录
     */
    fun cleanup(): Long {
        val beforeDateTime = LocalDateTime.now().minus(preloadProperties.accessRecordKeepDuration)
        val result = artifactAccessRecordDao.delete(beforeDateTime)
        logger.info("${result.deletedCount} artifact access record was deleted")
        return result.deletedCount
    }

    fun generateStrategy() {
        // TODO
    }

    @Async
    @EventListener(ArtifactResponseEvent::class)
    fun listen(event: ArtifactResponseEvent) {
        safeRecordArtifactCacheAccess(event.artifactResource)
    }

    /**
     * 记录缓存访问信息
     */
    private fun safeRecordArtifactCacheAccess(resource: ArtifactResource) {
        try {
            if (resource.node != null && !resource.node!!.folder) {
                recordSingleNode(resource)
            } else if (resource.nodes.isNotEmpty()) {
                recordMultiNode(resource)
            }
        } catch (ignore: Exception) {
            logger.warn("failed to record artifact cache access", ignore)
        }
    }

    private fun recordSingleNode(resource: ArtifactResource) {
        val cacheEnabled = resource.artifactMap.values.firstOrNull()?.getMetadata(METADATA_KEY_CACHE_ENABLED)
        val cacheMiss = cacheEnabled == true && resource.artifactMap.values.firstOrNull() !is FileArtifactInputStream
        onArtifactAccess(resource.node!!, cacheMiss)
    }

    private fun recordMultiNode(resource: ArtifactResource) {
        for (entry in resource.artifactMap) {
            val name = entry.key
            val ais = entry.value
            val cacheEnabled = ais.getMetadata(METADATA_KEY_CACHE_ENABLED)
            val node = resource.nodes.firstOrNull { name.endsWith(it.name) }
            if (node == null || node.folder) {
                continue
            }

            val cacheMiss = cacheEnabled == true && ais !is FileArtifactInputStream
            onArtifactAccess(resource.node!!, cacheMiss)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactAccessRecorder::class.java)
    }
}
