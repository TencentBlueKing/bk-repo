/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.replicator.base.internal

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaders
import com.tencent.bkrepo.fdtp.codec.FdtpResponseStatus
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.STORAGE_KEY
import com.tencent.bkrepo.replication.exception.ArtifactPushException
import com.tencent.bkrepo.replication.fdtp.FdtpAFTClientFactory
import com.tencent.bkrepo.replication.fdtp.FdtpServerProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.replica.base.process.ProgressListener
import com.tencent.bkrepo.replication.replica.context.FilePushContext
import io.netty.channel.ChannelProgressiveFuture
import io.netty.channel.ChannelProgressiveFutureListener
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.ConnectException
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty("fdtp.server.enabled")
class FdtpPusher(
    val localDataManager: LocalDataManager,
    val fdtpServerProperties: FdtpServerProperties,
    val listener: ProgressListener,
    val replicationProperties: ReplicationProperties,
) {

    /**
     * 使用fdtp推送blob文件数据到远程集群
     */
    fun pushBlob(filePushContext: FilePushContext): Boolean {
        with(filePushContext) {
            logger.info("File $sha256 will be pushed using the fdtp way.")
            val client = FdtpAFTClientFactory.createAFTClient(context.cluster, fdtpServerProperties.port)
            val artifactInputStream = localDataManager.getBlobData(sha256!!, size!!, context.localRepo)
            val rateLimitInputStream = artifactInputStream.rateLimit(
                replicationProperties.rateLimit.toBytes()
            )
            val storageKey = context.remoteRepo?.storageCredentials?.key
            val headers = DefaultFdtpHeaders()
            headers.add(SHA256, sha256)
            storageKey?.let { headers.add(STORAGE_KEY, storageKey) }
            try {
                val progressListener = object : ChannelProgressiveFutureListener {
                    private val tag = RequestTag(context.task, sha256, size)
                    private val progressListener: ProgressListener = listener
                    private var previous: Long = 0

                    @Throws(Exception::class)
                    override fun operationProgressed(future: ChannelProgressiveFuture?, progress: Long, total: Long) {
                        if (progress == previous) return
                        try {
                            progressListener.onProgress(tag.task, tag.key, progress - previous)
                        } catch (_: Exception) {
                        }
                        previous = progress
                    }

                    @Throws(Exception::class)
                    override fun operationComplete(future: ChannelProgressiveFuture) {
                        if (future.isSuccess) {
                            progressListener.onSuccess(tag.task)
                        } else {
                            progressListener.onFailed(tag.task, tag.key)
                        }
                    }
                }
                listener.onStart(context.task, sha256, 0)

                val responsePromise = client.sendStream(rateLimitInputStream, headers, progressListener)

                val response = responsePromise.get(READ_TIME_OUT, TimeUnit.SECONDS)
                if (response.status == FdtpResponseStatus.OK) {
                    return true
                } else {
                    val logMessage = "Error occurred while pushing file $sha256 " +
                            "with the fdtp way, error is ${response.status.reasonPhrase}"
                    logger.warn(logMessage)
                    throw ArtifactPushException(logMessage)
                }
            } catch (e: ConnectException) {
                // 当不支持fdtp方式进行传输时抛出异常，进行降级处理
                logger.warn(
                    "Error occurred while pushing file $sha256 with the fdtp way, errors is ${e.message}", e
                )
                throw ArtifactPushException(e.message.orEmpty(), HttpStatus.METHOD_NOT_ALLOWED.value)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FdtpPusher::class.java)
        // 读取结果返回超时时间 15分钟
        private const val READ_TIME_OUT = 60L * 15
    }
}
