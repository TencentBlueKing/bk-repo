/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.helm.listener

import com.tencent.bkrepo.common.api.util.toYamlString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.redis.RedisLock
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.helm.constants.buildRedisKey
import com.tencent.bkrepo.helm.listener.event.ChartDeleteEvent
import com.tencent.bkrepo.helm.listener.event.ChartVersionDeleteEvent
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.pojo.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.pojo.chart.ChartDeleteRequest
import com.tencent.bkrepo.helm.pojo.chart.ChartPackageDeleteRequest
import com.tencent.bkrepo.helm.pojo.chart.ChartVersionDeleteRequest
import com.tencent.bkrepo.helm.pool.HelmThreadPoolExecutor
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
import java.util.SortedSet
import java.util.concurrent.ThreadPoolExecutor

@Component
class ChartEventListener(
    private val redisOperation: RedisOperation
) : AbstractEventListener() {

    private val threadPoolExecutor: ThreadPoolExecutor = HelmThreadPoolExecutor.instance
    private val objectLock = Object()

    /**
     * 删除chart版本，更新index.yaml文件
     */
    @EventListener(ChartVersionDeleteEvent::class)
    fun handle(event: ChartVersionDeleteEvent) {
        // 如果index.yaml文件不存在，说明还没有初始化该文件，return
        // 如果index.yaml文件存在，则进行更新
        with(event.request) {
            logger.info("handling chart version delete event for [$name@$version] in repo [$projectId/$repoName]")
            if (!exist(projectId, repoName, HelmUtils.getIndexYamlFullPath())) {
                logger.warn("Index yaml file is not initialized in repo [$projectId/$repoName], return.")
                return
            }
            val task = {
                val lockKey = buildRedisKey(projectId, repoName)
                val lock = RedisLock(redisOperation, lockKey, expiredTimeInSeconds)
                val isLocked = try {
                    lock.tryLock()
                } catch (exception: RuntimeException) {
                    logger.debug(
                        "acquire redis distribute lock [$lockKey] for delete chart version [$name@$version] failed."
                    )
                    false
                }
                if (isLocked) {
                    logger.info("execute update index yaml for delete package [$name], version [$version]" +
                        " with redis distribute lock [$lockKey].")
                    lock.use {
                        doRefreshIndexForDeleteVersion(this)
                    }
                } else {
                    logger.info("execute update index yaml for delete package [$name], version [$version]" +
                        " with synchronized lock [$objectLock].")
                    synchronized(objectLock) {
                        doRefreshIndexForDeleteVersion(this)
                    }
                }
            }
            threadPoolExecutor.submit(task)
        }
    }

    private fun doRefreshIndexForDeleteVersion(chartVersionDeleteRequest: ChartVersionDeleteRequest) {
        with(chartVersionDeleteRequest) {
            try {
                logger.debug(
                    "do refresh index yaml for delete chart version [$name@$version] in repo [$projectId/$repoName]"
                )
                val stopWatch = StopWatch("getOriginalIndexYamlFile for delete chart version [$name@$version]")
                stopWatch.start()
                val originalIndexYamlMetadata = getOriginalIndexYaml(projectId, repoName)
                stopWatch.stop()
                logger.debug("query index file metadata : $stopWatch")
                val entries = originalIndexYamlMetadata.entries
                // 如果不包含该包或者该版本说明索引还未刷新
                if (!entries.containsKey(name)) {
                    logger.info("The metadata [$name] was not matched in the index file, return.")
                    return
                }
                if (entries[name].orEmpty().none { it.version == version }) {
                    logger.info(
                        "The metadata [$name] with version [$version] was not matched in the index file, return."
                    )
                    return
                }
                val chartMetadataSet = entries[name]!!
                logger.debug("delete version: original entries size: [${entries.size}], " +
                    "chart [$name] metadata size: [${chartMetadataSet.size}]")
                if (chartMetadataSet.size == 1 && (version == chartMetadataSet.first().version)) {
                    entries.remove(name)
                } else {
                    updateIndexYaml(version, chartMetadataSet)
                }
                logger.debug("delete version: updated entries size: [${entries.size}], " +
                    "chart [$name] metadata size: [${chartMetadataSet.size}]")
                val (artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(
                    originalIndexYamlMetadata, this
                )
                uploadIndexYamlMetadata(artifactFile, nodeCreateRequest)
                logger.info(
                    "User [$operator] fresh index.yaml for delete chart [$name], version [$version] " +
                        "in repo [$projectId/$repoName] success!"
                )
            } catch (exception: Exception) {
                logger.error(
                    "User [$operator] fresh index.yaml for delete chart [$name], version [$version] " +
                        "in repo [$projectId/$repoName] failed, message: $exception"
                )
                throw exception
            }
        }
    }

    private fun updateIndexYaml(version: String, chartMetadataSet: SortedSet<HelmChartMetadata>) {
        run stop@{
            chartMetadataSet.forEachIndexed { _, helmChartMetadata ->
                if (version == helmChartMetadata.version) {
                    chartMetadataSet.remove(helmChartMetadata)
                    return@stop
                }
            }
        }
    }

    private fun buildFileAndNodeCreateRequest(
        indexYamlMetadata: HelmIndexYamlMetadata,
        request: ChartDeleteRequest
    ): Pair<ArtifactFile, NodeCreateRequest> {
        val artifactFile = ArtifactFileFactory.build(indexYamlMetadata.toYamlString().byteInputStream())
        val nodeCreateRequest = with(request) {
            NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = HelmUtils.getIndexYamlFullPath(),
                size = artifactFile.getSize(),
                sha256 = artifactFile.getFileSha256(),
                md5 = artifactFile.getFileMd5(),
                overwrite = true,
                operator = operator
            )
        }
        return Pair(artifactFile, nodeCreateRequest)
    }

    /**
     * 删除chart版本，更新index.yaml文件
     */
    @EventListener(ChartDeleteEvent::class)
    fun handle(event: ChartDeleteEvent) {
        with(event.requestPackage) {
            logger.info("handling chart delete event for [$name] in repo [$projectId/$repoName]")
            if (!exist(projectId, repoName, HelmUtils.getIndexYamlFullPath())) {
                logger.warn("Index yaml file is not initialized in repo [$projectId/$repoName], return.")
                return
            }
            val task = {
                val lockKey = buildRedisKey(projectId, repoName)
                val lock = RedisLock(redisOperation, lockKey, expiredTimeInSeconds)
                val isLocked = try {
                    lock.tryLock()
                } catch (exception: RuntimeException) {
                    logger.debug(
                        "acquire redis distribute lock [$lockKey] for delete chart [$name] failed."
                    )
                    false
                }
                if (isLocked) {
                    logger.info("execute update index yaml for delete package [$name] " +
                        "with redis distribute lock [$lockKey].")
                    lock.use {
                        doRefreshIndexForDeletePackage(this)
                    }
                } else {
                    logger.info("execute update index yaml for delete package [$name] " +
                        "with synchronized lock [$objectLock].")
                    synchronized(objectLock) {
                        doRefreshIndexForDeletePackage(this)
                    }
                }
            }
            threadPoolExecutor.submit(task)
        }
    }

    private fun doRefreshIndexForDeletePackage(chartPackageDeleteRequest: ChartPackageDeleteRequest) {
        with(chartPackageDeleteRequest) {
            try {
                val stopWatch = StopWatch("getOriginalIndexYamlFile for delete chart [$name]")
                stopWatch.start()
                val originalIndexYamlMetadata = getOriginalIndexYaml(projectId, repoName)
                stopWatch.stop()
                logger.debug("query index file metadata : $stopWatch")
                // 需要进行判断，如果是上传的包索引延迟刷新导致这里里面可能没这条数据
                if (!originalIndexYamlMetadata.entries.containsKey(name)) {
                    logger.info("The chart metadata [$name] was not matched in the index file, return.")
                    return
                }
                originalIndexYamlMetadata.entries.remove(name)
                val (artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(
                    originalIndexYamlMetadata, this
                )
                uploadIndexYamlMetadata(artifactFile, nodeCreateRequest)
                logger.info(
                    "User [$operator] fresh index.yaml for delete chart [$name] " +
                        "in repo [$projectId/$repoName] success!"
                )
            } catch (exception: Exception) {
                logger.error(
                    "User [$operator] fresh index.yaml for delete chart [$name] " +
                        "in repo [$projectId/$repoName] failed, message: $exception"
                )
                throw exception
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ChartEventListener::class.java)

        /**
         * 定义Redis过期时间
         */
        private const val expiredTimeInSeconds: Long = 5 * 60 * 1000L
    }
}
