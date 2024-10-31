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

package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.api.ServiceProxyClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactResponseException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.RepositoryId
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResourceWriter
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.bound
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.util.AESUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.common.service.util.UrlUtils
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.config.GenericProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption

@Service
class ProxyService(
    private val serviceProxyClient: ServiceProxyClient,
    private val artifactResourceWriter: ArtifactResourceWriter,
    private val permissionManager: PermissionManager,
    private val genericProperties: GenericProperties,
    private val clusterProperties: ClusterProperties,
    private val storageManager: StorageManager
): ArtifactService() {

    fun download(projectId: String, name: String) {
        permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectId)
        val repo = ArtifactContextHolder.getRepoDetail(
            RepositoryId(
                genericProperties.proxy.projectId,
                genericProperties.proxy.repoName
            )
        )
        val artifactInfo = GenericArtifactInfo(
            genericProperties.proxy.projectId,
            genericProperties.proxy.repoName,
            genericProperties.proxy.fullPath
        )
        val node = ArtifactContextHolder.getNodeDetail(artifactInfo)
            ?: throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
        val inputStream = storageManager.loadArtifactInputStream(node, repo.storageCredentials)
            ?: throw ArtifactNotFoundException(node.fullPath)
        val tmpJar = writeProxyProperties(inputStream, projectId, name)
        downloadProxy(tmpJar, projectId, name)
    }

    private fun downloadProxy(proxyJar: File, projectId: String, name: String) {
        val range = if (HeaderUtils.getHeader(HttpHeaders.RANGE).isNullOrBlank()) {
            Range.full(proxyJar.length())
        } else {
            HttpRangeUtils.resolveRange(HttpContextHolder.getRequest(), proxyJar.length())
        }
        try {
            artifactResourceWriter.write(
                ArtifactResource(
                    inputStream = ArtifactInputStream(proxyJar.bound(range), range),
                    artifactName = "proxy.jar",
                    useDisposition = true
                )
            )
        } catch (e: ArtifactResponseException) {
            val principal = SecurityUtils.getPrincipal()
            val message = LocaleMessageUtils.getLocalizedMessage(e.messageCode, e.params)
            val code = e.messageCode.getCode()
            val clientAddress = HttpContextHolder.getClientAddress()
            val xForwardedFor = HttpContextHolder.getXForwardedFor()
            logger.warn(
                "User[$principal],ip[$clientAddress] download proxy[$projectId/$name] failed[$code]$message" +
                    " X_FORWARDED_FOR: $xForwardedFor"
            )
        } finally {
            logger.info(proxyJar.absolutePath)
            proxyJar.delete()
        }
    }

    private fun writeProxyProperties(proxyJar: ArtifactInputStream, projectId: String, name: String): File {
        try {
            val secretKey = getSecretKey(projectId, name)
            val tmpJar = Files.createTempFile("proxy", ".jar").toFile()
            tmpJar.outputStream().use { proxyJar.copyTo(it) }
            val fs = FileSystems.newFileSystem(URI.create("jar:${tmpJar.toURI()}"), mapOf("create" to "true"))
            val properties = fs.getPath("BOOT-INF/classes/.proxy.properties")
            val writer = Files.newBufferedWriter(
                properties,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            val content = String.format(
                FORMAT,
                projectId, name, secretKey,
                clusterProperties.self.name,
                UrlUtils.extractDomain(clusterProperties.self.url)
            )
            writer.write(content)
            writer.close()
            fs.close()
            return tmpJar
        } catch (e: Exception) {
            throw ArtifactNotFoundException("proxy.jar")
        }
    }

    private fun getSecretKey(projectId: String, name: String) = try {
        val encryptedKey = serviceProxyClient.getEncryptedKey(projectId, name).data!!
        AESUtils.decrypt(encryptedKey.encSecretKey)
    } catch (e: RemoteErrorCodeException) {
        throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, name)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyService::class.java)
        private const val FORMAT = """
bkrepo.proxy.project.id=%s
bkrepo.proxy.name=%s
bkrepo.proxy.secret.key=%s
bkrepo.proxy.cluster.name=%s
bkrepo.gateway=%s
        """
    }
}
