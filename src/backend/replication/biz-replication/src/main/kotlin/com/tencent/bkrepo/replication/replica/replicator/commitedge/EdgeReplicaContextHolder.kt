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

package com.tencent.bkrepo.replication.replica.replicator.commitedge

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.net.speedtest.Counter
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.pojo.task.EdgeReplicaTaskRecord
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.async.DeferredResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EdgeReplicaContextHolder {

    private val logger = LoggerFactory.getLogger(EdgeReplicaContextHolder::class.java)
    private val deferredResultMap = ConcurrentHashMap<String, DeferredResult<Response<EdgeReplicaTaskRecord>>>()

    private const val HOST_KEY = "host"
    private const val RATE_KEY = "rate"
    private const val MIN_TIMEOUT = 60.0
    private const val DEFAULT_RATE = 1.0

    fun addDeferredResult(
        clusterName: String,
        replicatingNum: Int,
        deferredResult: DeferredResult<Response<EdgeReplicaTaskRecord>>
    ) {
        val key = "$clusterName-${UUID.randomUUID()}-$replicatingNum"
        deferredResult.onCompletion {
            logger.info("remove key on completion: $key")
            deferredResultMap.remove(key)
        }
        deferredResult.onTimeout {
            logger.info("remove key on timeout: $key")
            deferredResultMap.remove(key)
        }
        deferredResult.onError {
            logger.info("remove key on error: $key")
            deferredResultMap.remove(key)
        }
        deferredResultMap[key] = deferredResult
    }

    @Suppress("LoopWithTooManyJumpStatements")
    fun setEdgeReplicaTask(edgeReplicaTaskRecord: EdgeReplicaTaskRecord) {
        with(edgeReplicaTaskRecord) {
            var retryTime = 12
            while (retryTime > 0) {
                retryTime--
                val key = deferredResultMap.keys().toList()
                    .filter { it.startsWith(edgeReplicaTaskRecord.execClusterName) }
                    .minByOrNull { it.split("-").last().toInt() }
                if (key == null) {
                    logger.info("key is null: ${edgeReplicaTaskRecord.execClusterName}")
                    Thread.sleep(5000)
                    continue
                }
                val deferredResult = deferredResultMap[key]
                if (deferredResult == null || deferredResult.isSetOrExpired) {
                    logger.info("deferredReuslt is invaild: $key")
                    Thread.sleep(5000)
                    continue
                }

                if (!deferredResult.setResult(ResponseBuilder.success(this))) {
                    logger.info("deferredResult set failed: $key")
                    continue
                }
                logger.info("send edge task: $edgeReplicaTaskRecord")
                deferredResultMap.remove(key)
                return
            }
            logger.error("no edge cluster node claim task")
            throw ErrorCodeException(
                ReplicationMessageCode.REPLICA_TASK_TIMEOUT,
                edgeReplicaTaskRecord.taskDetail.task.id
            )
        }
    }

    fun getEstimatedTime(timoutCheckHosts: List<Map<String, String>>, url: String, size: Long): Long {
        val rate = timoutCheckHosts.firstOrNull { url.contains(it[HOST_KEY].toString()) }
            ?.get(RATE_KEY)?.toDouble() ?: DEFAULT_RATE
        val estimatedTime = if (size <= MIN_TIMEOUT * Counter.MB * rate) {
            MIN_TIMEOUT
        } else {
            size / Counter.MB / rate
        } * 1.5
        logger.info("replica to $url maybe will cost $estimatedTime seconds to transfer, size is $size")
        return estimatedTime.toLong()
    }
}
