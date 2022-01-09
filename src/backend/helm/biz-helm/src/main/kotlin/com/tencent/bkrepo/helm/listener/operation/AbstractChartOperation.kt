/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.helm.listener.operation

import com.tencent.bkrepo.common.redis.RedisLock
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.helm.constants.REDIS_LOCK_KEY_PREFIX
import com.tencent.bkrepo.helm.pojo.chart.ChartOperationRequest
import com.tencent.bkrepo.helm.pojo.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.service.impl.AbstractChartService
import com.tencent.bkrepo.helm.utils.ObjectBuilderUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.StopWatch

abstract class AbstractChartOperation(
    private val request: ChartOperationRequest,
    private val redisOperation: RedisOperation,
    private val chartService: AbstractChartService
) : Runnable {
    override fun run() {
        with(request) {
            val lock = initRedisLock(projectId, repoName)
            if (getSpinLock(lock)) {
                logger.info(
                    "Prepare to refresh index.yaml with redis distribute lock."
                )
                lock.use {
                    handleOperation(this)
                }
            }
        }
    }

    /**
     * 处理对应的chart操作用于更新index.yaml文件
     */
    private fun handleOperation(request: ChartOperationRequest) {
        with(request) {
            try {
                val stopWatch = StopWatch(
                    "getOriginalIndexYamlFile for refreshing index.yaml in repo [$projectId/$repoName]"
                )
                stopWatch.start()
                val originalIndexYamlMetadata = chartService.getOriginalIndexYaml(projectId, repoName)
                stopWatch.stop()
                logger.info("query index file metadata cost: ${stopWatch.totalTimeSeconds}s")
                handleEvent(originalIndexYamlMetadata)
                logger.info("index.yaml is ready to upload...")
                val (artifactFile, nodeCreateRequest) = ObjectBuilderUtil.buildFileAndNodeCreateRequest(
                    originalIndexYamlMetadata, this
                )
                chartService.uploadIndexYamlMetadata(artifactFile, nodeCreateRequest)
                logger.info(
                    "Index.yaml has been refreshed by User [$operator] " +
                        "in repo [$projectId/$repoName] !"
                )
            } catch (e: Exception) {
                logger.error(
                    "Error [${e.message}] occurred while refreshing index.yaml by" +
                        " User [$operator] in repo [$projectId/$repoName] !"
                )
                throw e
            }
        }
    }

    /**
     * 处理对应的事件用于更新index.yaml中的meta data
     */
    open fun handleEvent(helmIndexYamlMetadata: HelmIndexYamlMetadata) {}

    /**
     * 自旋获取redis锁
     */
    private fun getSpinLock(
        lock: RedisLock,
        sleepTime: Long = SPIN_SLEEP_TIME,
        retryTimes: Int = RETRY_TIMES
    ): Boolean {
        logger.info("Start to get redis lock to fresh index.yaml..")
        loop@ for (i in 0 until retryTimes) {
            when {
                lock.tryLock() -> return true
                else -> try {
                    Thread.sleep(sleepTime)
                } catch (e: InterruptedException) {
                    continue@loop
                }
            }
        }
        logger.info("Could not get redis lock after $retryTimes times...")
        return false
    }

    /**
     * 初始化redislock
     */
    private fun initRedisLock(projectId: String, repoName: String): RedisLock {
        val lockKey = buildRedisKey(projectId, repoName)
        return RedisLock(redisOperation, lockKey, EXPIRED_TIME_IN_SECONDS)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(AbstractChartOperation::class.java)

        /**
         * 定义Redis过期时间
         */
        private const val EXPIRED_TIME_IN_SECONDS: Long = 5 * 60 * 1000L
        private const val SPIN_SLEEP_TIME: Long = 30L
        private const val RETRY_TIMES: Int = 10000

        fun buildRedisKey(projectId: String, repoName: String): String = "$REDIS_LOCK_KEY_PREFIX$projectId/$repoName"
    }
}
