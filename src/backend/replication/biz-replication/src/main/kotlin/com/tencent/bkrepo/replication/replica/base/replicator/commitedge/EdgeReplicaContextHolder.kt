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

package com.tencent.bkrepo.replication.replica.base.replicator.commitedge

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.task.EdgeReplicaTaskRecord
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.async.DeferredResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EdgeReplicaContextHolder {

    private val logger = LoggerFactory.getLogger(EdgeReplicaContextHolder::class.java)
    private val deferredResultMap = ConcurrentHashMap<String, DeferredResult<Response<EdgeReplicaTaskRecord>>>()

    fun addDeferredResult(clusterName: String, deferredResult: DeferredResult<Response<EdgeReplicaTaskRecord>>) {
        val key = "$clusterName-${UUID.randomUUID()}"
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

    fun setEdgeReplicaTask(edgeReplicaTaskRecord: EdgeReplicaTaskRecord) {
        with(edgeReplicaTaskRecord) {
            var retryTime = 12
            while (retryTime > 0) {
                retryTime--
                val key = deferredResultMap.keys().toList()
                    .firstOrNull { it.startsWith(edgeReplicaTaskRecord.execClusterName) }
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
                logger.info("send edge task: $edgeReplicaTaskRecord")
                deferredResult.setResult(ResponseBuilder.success(this))
                deferredResultMap.remove(key)
                break
            }
        }
    }
}
