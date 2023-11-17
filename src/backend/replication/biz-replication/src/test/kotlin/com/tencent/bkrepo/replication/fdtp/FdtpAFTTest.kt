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

package com.tencent.bkrepo.replication.fdtp

import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurer
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_UPLOADING_TIME
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.proxy.ProxyRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurity
import com.tencent.bkrepo.common.security.service.ServiceAuthManager
import com.tencent.bkrepo.common.security.service.ServiceAuthProperties
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitorHelper
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaders
import com.tencent.bkrepo.fdtp.codec.FdtpResponseStatus
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.springframework.cloud.loadbalancer.support.SimpleObjectProvider
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.springframework.cloud.sleuth.Tracer
import org.springframework.cloud.sleuth.otel.bridge.OtelTracer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FdtpAFTTest {
    private val server: FdtpAFTServer
    val handler = SimpleFdtpAFTRequestHandler()
    val client: FdtpAFTClient
    val clientFactory: FdtpAFTClientFactory

    init {
        mockPrerequisites()
        val secretKey = "ut-secret-key"
        val serverProperties = ServiceAuthProperties(enabled = true, secretKey = secretKey)
        val serviceAuthManager = ServiceAuthManager(serverProperties)
        val fdtpAuthManager = FdtpAuthManager(serviceAuthManager)
        val fdtpServerProperties = FdtpServerProperties()
        server = FdtpAFTServer(fdtpServerProperties, handler, fdtpAuthManager)
        val clusterInfo = ClusterInfo(
            url = "http://127.0.0.1:8080/replication",
            udpPort = fdtpServerProperties.port
        )
        val fdtpClientProperties = FdtpClientProperties(chunkSize = 8192)
        clientFactory = FdtpAFTClientFactory(fdtpAuthManager, fdtpClientProperties)
        client = FdtpAFTClientFactory.createAFTClient(clusterInfo)
    }

    private fun mockPrerequisites() {
        val artifactConfigurer = Mockito.mock(ArtifactConfigurer::class.java)
        val compositeRepository = Mockito.mock(CompositeRepository::class.java)
        val proxyRepository = Mockito.mock(ProxyRepository::class.java)
        val repositoryClient = Mockito.mock(RepositoryClient::class.java)
        val nodeClient = Mockito.mock(NodeClient::class.java)
        val httpAuthSecurity = SimpleObjectProvider<HttpAuthSecurity>(null)
        ArtifactContextHolder(
            listOf(artifactConfigurer),
            compositeRepository,
            proxyRepository,
            repositoryClient,
            nodeClient,
            httpAuthSecurity,
        )
        val helper = StorageHealthMonitorHelper(ConcurrentHashMap())
        ArtifactFileFactory(StorageProperties(), helper)
        mockkObject(ArtifactMetrics)
        every { ArtifactMetrics.getUploadingCounters(any()) } returns emptyList()
        every { ArtifactMetrics.getUploadingTimer(any()) } returns Timer.builder(ARTIFACT_UPLOADING_TIME)
            .register(SimpleMeterRegistry())
        mockkObject(SpringContextUtils.Companion)
        every { SpringContextUtils.publishEvent(any()) } returns Unit
    }

    @BeforeAll
    fun startFdtpServer() {
        val tracer = mockk<OtelTracer>()
        mockkObject(SpringContextUtils.Companion)
        every { SpringContextUtils.getBean<Tracer>() } returns tracer
        every { tracer.currentSpan() } returns null
        every { SpringContextUtils.publishEvent(any()) } returns Unit
        server.start()
    }

    @AfterAll
    fun stopFdtpServer() {
        clientFactory.destroy()
        server.stop()
    }

    @Test
    fun sendFileTest() {
        val file = createTempFile()
        val fileSize = 1024 * 1024
        val data = Random.nextBytes(fileSize)
        file.writeBytes(data)
        val headers = DefaultFdtpHeaders()
        val sha256 = file.sha256()
        headers.add(SHA256, sha256)
        val responsePromise = client.sendFile(file, headers)
        val response = responsePromise.get(3, TimeUnit.SECONDS)
        val request = handler.fileMap[response.stream.id()]
        // 确定请求能被正确接收
        Assertions.assertNotNull(request)
        // 确定参数能被正确接收
        Assertions.assertEquals(sha256, request!!.headers.get(SHA256))
        // 确定文件能被正确接受
        Assertions.assertEquals(sha256, request.artifactFile.getFileSha256())
    }

    @Test
    fun sendStreamTest() {
        val size = 1024 * 1024
        val data = Random.nextBytes(size)
        val inputStream = ByteArrayInputStream(data)
        val headers = DefaultFdtpHeaders()
        val sha256 = data.inputStream().sha256()
        headers.add(SHA256, sha256)
        val responsePromise = client.sendStream(inputStream, headers)
        val response = responsePromise.get(3, TimeUnit.SECONDS)
        val request = handler.fileMap[response.stream.id()]
        // 确定请求能被正确接收
        Assertions.assertNotNull(request)
        // 确定参数能被正确接收
        Assertions.assertEquals(sha256, request!!.headers.get(SHA256))
        // 确定数据能被正确接受
        Assertions.assertEquals(sha256, request.artifactFile.getFileSha256())
    }

    class SimpleFdtpAFTRequestHandler : FdtpAFTRequestHandler {
        val fileMap = mutableMapOf<Int, FullFdtpAFTRequest>()
        override fun handler(request: FullFdtpAFTRequest): FdtpResponseStatus {
            val streamId = request.stream.id()
            fileMap[streamId] = request
            return FdtpResponseStatus.OK
        }
    }
}
