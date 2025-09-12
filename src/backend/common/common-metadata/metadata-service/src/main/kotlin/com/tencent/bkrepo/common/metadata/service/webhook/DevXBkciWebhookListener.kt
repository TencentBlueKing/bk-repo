/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.webhook

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.CUSTOM
import com.tencent.bkrepo.common.artifact.constant.LOG
import com.tencent.bkrepo.common.artifact.constant.LSYNC
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.constant.REPORT
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyChannelSetting
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.ProxyConfiguration
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.common.service.util.okhttp.PlatformAuthInterceptor
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.webhook.BkCiDevXEnabledPayload
import io.micrometer.observation.ObservationRegistry
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * DevX环境bkci webhook监听器
 */
@Component
@Conditional(SyncCondition::class)
class DevXBkciWebhookListener(
    private val devXProperties: DevXProperties,
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val registry: ObservationRegistry
) : BkciWebhookListener {

    private val client by lazy { createClient() }

    override fun onDevXEnabled(payload: BkCiDevXEnabledPayload) {
        // 创建远程制品库集群仓库
        if (devXProperties.remoteBkRepoUrl.isNotEmpty()) {
            createRemoteRepo(payload.projectCode, LSYNC)
            createRemoteRepo(payload.projectCode, "$PIPELINE$DEVX_SUFFIX")
            createRemoteRepo(payload.projectCode, "$CUSTOM$DEVX_SUFFIX")
            createRemoteRepo(payload.projectCode, "$REPORT$DEVX_SUFFIX")
            createRemoteRepo(payload.projectCode, LOG)
        }

        // 创建本地项目及仓库
        createLocalProject(payload)

        // 创建本地仓库
        createLocalCompositeRepo(payload.projectCode, LSYNC, LSYNC, true)
        createLocalCompositeRepo(payload.projectCode, PIPELINE, "$PIPELINE$DEVX_SUFFIX", true)
        createLocalCompositeRepo(payload.projectCode, CUSTOM, "$CUSTOM$DEVX_SUFFIX", true)
        createLocalCompositeRepo(payload.projectCode, REPORT, "$REPORT$DEVX_SUFFIX", false)
        createLocalCompositeRepo(payload.projectCode, LOG, LOG, false)
    }

    /**
     * 在远程制品库集群创建仓库
     */
    private fun createRemoteRepo(projectId: String, repoName: String) {
        val url = "${devXProperties.remoteBkRepoUrl}/repository/api/repo/create"
        val body = UserRepoCreateRequest(
            projectId = projectId,
            name = repoName,
            type = RepositoryType.GENERIC,
            category = RepositoryCategory.COMPOSITE,
            display = false
        ).toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).execute().use { res ->
            if (res.isSuccessful) {
                logger.info("create remote repo[$projectId/$repoName] success")
                return
            }

            // 输出请求错误日志
            val msg = res.body?.string()
            val errorLog = "create remote repo[$projectId/$repoName] failed, code[${res.code}], msg[$msg]"
            try {
                val response = msg?.readJsonString<Response<*>>()
                // 忽略仓库已存在的错误
                if (response?.code == ArtifactMessageCode.REPOSITORY_EXISTED.getCode()) {
                    logger.info(errorLog)
                    return
                }
            } catch (ignore: Exception) {
                // ignore
            }
            logger.error(errorLog)
        }
    }

    /**
     * 创建本地项目
     */
    private fun createLocalProject(payload: BkCiDevXEnabledPayload) {
        val metadata = ArrayList<ProjectMetadata>(8)
        payload.bgId?.let { metadata.add(ProjectMetadata(ProjectMetadata.KEY_BG_ID, it)) }
        payload.bgName?.let { metadata.add(ProjectMetadata(ProjectMetadata.KEY_BG_NAME, it)) }
        payload.deptId?.let { metadata.add(ProjectMetadata(ProjectMetadata.KEY_DEPT_ID, it)) }
        payload.deptName?.let { metadata.add(ProjectMetadata(ProjectMetadata.KEY_DEPT_NAME, it)) }
        payload.centerId?.let { metadata.add(ProjectMetadata(ProjectMetadata.KEY_CENTER_ID, it)) }
        payload.centerName?.let { metadata.add(ProjectMetadata(ProjectMetadata.KEY_CENTER_NAME, it)) }
        payload.productId?.let { metadata.add(ProjectMetadata(ProjectMetadata.KEY_PRODUCT_ID, it)) }
        metadata.add(ProjectMetadata(ProjectMetadata.KEY_ENABLED, true))
        val request = ProjectCreateRequest(
            name = payload.projectCode,
            displayName = payload.projectName,
            metadata = metadata,
        )
        try {
            projectService.createProject(request)
            logger.info("create project[${payload.projectCode}] success")
        } catch (e: Exception) {
            if (e is ErrorCodeException && e.messageCode == ArtifactMessageCode.PROJECT_EXISTED) {
                logger.info("project[${payload.projectCode}] already exists")
            } else {
                logger.error("create project[${payload.projectCode}] failed", e)
            }
        }
    }

    /**
     * 创建代理远程集群同名仓库的本地仓库
     */
    private fun createLocalCompositeRepo(
        projectId: String,
        repoName: String,
        remoteRepoName: String,
        display: Boolean,
        retry: Int = 3
    ) {
        val configuration = if (devXProperties.remoteBkRepoUrl.isNotEmpty()) {
            val proxyConfiguration = ProxyConfiguration(
                listOf(
                    ProxyChannelSetting(
                        public = false,
                        name = remoteRepoName,
                        url = "${devXProperties.remoteBkRepoUrl}/generic/$projectId/$remoteRepoName"
                    )
                )
            )
            CompositeConfiguration(proxyConfiguration)
        } else {
            CompositeConfiguration()
        }
        val request = RepoCreateRequest(
            projectId = projectId,
            name = repoName,
            type = RepositoryType.GENERIC,
            category = RepositoryCategory.COMPOSITE,
            public = false,
            configuration = configuration,
            display = display
        )

        try {
            repositoryService.createRepo(request)
            logger.info("create repo[$projectId/$repoName] success")
        } catch (e: Exception) {
            if (e is ErrorCodeException && e.messageCode == ArtifactMessageCode.REPOSITORY_EXISTED) {
                logger.info("repo[$projectId/$repoName] already exists")
            } else if (e is ErrorCodeException && e.messageCode == ArtifactMessageCode.PROJECT_NOT_FOUND) {
                if (retry > 0) {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(3))
                    createLocalCompositeRepo(projectId, repoName, remoteRepoName, display, retry - 1)
                }
            } else {
                logger.error("create repo[$projectId/$repoName] failed", e)
            }
        }
    }

    private fun createClient(): OkHttpClient {
        val builder = HttpClientBuilderFactory.create(registry = registry)
        val ak = devXProperties.remoteBkRepoAccessKey
        val sk = devXProperties.remoteBkRepoSecretKey
        // 使用系统用户身份操作
        builder.addInterceptor(PlatformAuthInterceptor(ak, sk, devXProperties.remoteBkRepoUser))
        if (devXProperties.remoteBkRepoIp.isNotEmpty()) {
            val remoteBkRepoUrl = devXProperties.remoteBkRepoUrl.toHttpUrl()
            val remoteBkRepoIp = devXProperties.remoteBkRepoIp
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return if (hostname == remoteBkRepoUrl.host) {
                        listOf(Inet4Address.getByName(remoteBkRepoIp))
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
                }
            })
        }
        return builder.build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DevXBkciWebhookListener::class.java)
        private const val DEVX_SUFFIX = "-devx"
    }
}
