/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.innercos.client

import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.endpoint.DefaultEndpointResolver
import com.tencent.bkrepo.common.storage.innercos.endpoint.EndpointBuilder
import com.tencent.bkrepo.common.storage.innercos.endpoint.EndpointResolver
import com.tencent.bkrepo.common.storage.innercos.endpoint.InnerCosEndpointBuilder
import com.tencent.bkrepo.common.storage.innercos.endpoint.PolarisEndpointResolver
import com.tencent.bkrepo.common.storage.innercos.endpoint.PublicCosEndpointBuilder
import com.tencent.bkrepo.common.storage.innercos.endpoint.PublicCosInnerEndpointBuilder
import com.tencent.bkrepo.common.storage.innercos.http.HttpProtocol
import org.springframework.util.unit.DataSize
import java.time.Duration

/**
 * cos 客户端配置
 */
class ClientConfig(private val credentials: InnerCosCredentials) {
    /**
     * 分片上传最大分片数量
     */
    val maxUploadParts: Int = MAX_PARTS

    /**
     * 分片上传最小数量
     */
    val minimumUploadPartSize: Long = DataSize.ofMegabytes(MIN_PART_SIZE).toBytes()

    /**
     * 签名过期时间
     */
    var signExpired: Duration = Duration.ofDays(1)

    /**
     * http协议
     */
    var httpProtocol: HttpProtocol = HttpProtocol.HTTP

    /**
     * 分片阈值，大于此值将采用分片上传/下载
     */
    val multipartThreshold: Long = DataSize.ofMegabytes(MULTIPART_THRESHOLD_SIZE).toBytes()

    /**
     * 分片下载最大分片数量
     */
    val maxDownloadParts: Int = credentials.download.maxDownloadParts

    /**
     * 分片下载最小数量
     */
    val minimumDownloadPartSize: Long = DataSize.ofMegabytes(credentials.download.minimumPartSize).toBytes()

    /**
     * cos访问域名构造器
     */
    val endpointBuilder = createEndpointBuilder()

    /**
     * cos访问域名解析器
     */
    var endpointResolver = createEndpointResolver()

    /**
     * 记录慢日志的网络速度阈值，即网络速度低于这个速度，则记录慢日志。
     * */
    val slowLogSpeed = credentials.slowLogSpeed

    /**
     * 记录慢日志的时间阈值，即执行时间超过这个时间，则记录慢日志。
     * */
    val slowLogTime: Long = credentials.slowLogTimeInMillis

    /**
     * 下载并发数。增加并发以增加带宽利用率（因为可能存在单连接限速的情况），
     * 但是数值不是越大越好，当下行带宽打满，再增加并发数，反而导致单连接的速度下降。
     * */
    val downloadWorkers: Int = credentials.download.workers

    /**
     * 下载时间高水位线。超过该值，则降级为单连接下载
     * */
    var downloadTimeHighWaterMark: Long = credentials.download.downloadTimeHighWaterMark

    /**
     * 下载时间低水位线。低于该值时，则恢复多连接下载
     * */
    var downloadTimeLowWaterMark: Long = credentials.download.downloadTimeLowWaterMark

    /**
     * 分块下载任务间隔时间。
     * 为保证大文件分块下载不占满工作线程，以保证新进来的连接也能开始下载，
     * 所以采取了一个间隔时间配置。即一定时间的连接允许插入（即可以开始下载）。
     * */
    var downloadTaskInterval: Long = credentials.download.taskInterval

    var timeout: Long = credentials.download.timeout

    /**
     * 下载分块的qps限速
     * */
    var qps: Int = credentials.download.qps

    private fun createEndpointResolver(): EndpointResolver {
        return if (credentials.modId != null && credentials.cmdId != null) {
            PolarisEndpointResolver(credentials.modId!!, credentials.cmdId!!)
        } else {
            DefaultEndpointResolver()
        }
    }

    private fun createEndpointBuilder(): EndpointBuilder {
        with(credentials) {
            if (public && inner) {
                return PublicCosInnerEndpointBuilder()
            }
            return if (credentials.public) PublicCosEndpointBuilder() else InnerCosEndpointBuilder()
        }
    }

    companion object {
        private const val MAX_PARTS = 10000
        private const val MULTIPART_THRESHOLD_SIZE = 10L
        private const val MIN_PART_SIZE = 10L
    }
}
