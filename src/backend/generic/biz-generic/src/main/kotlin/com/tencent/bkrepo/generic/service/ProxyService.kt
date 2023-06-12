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
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.resolve.response.DefaultArtifactResourceWriter
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.util.AESUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.util.UrlUtils
import com.tencent.bkrepo.generic.config.GenericProperties
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
    private val artifactResourceWriter: DefaultArtifactResourceWriter,
    private val permissionManager: PermissionManager,
    private val genericProperties: GenericProperties,
    private val clusterProperties: ClusterProperties
) {

    fun download(projectId: String, name: String) {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId)
        val encryptedKey = try {
            serviceProxyClient.getEncryptedKey(projectId, name).data!!
        } catch (e: RemoteErrorCodeException) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, name)
        }
        val proxyJar = File(genericProperties.proxyPath)
        try {
            val fs = FileSystems.newFileSystem(URI.create("jar:${proxyJar.toURI()}"), mapOf("create" to "true"))
            val properties = fs.getPath("BOOT-INF/classes/.proxy.properties")
            val writer = Files.newBufferedWriter(
                properties,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            val content = String.format(
                FORMAT,
                projectId, name, AESUtils.decrypt(encryptedKey.encSecretKey),
                clusterProperties.self.name, UrlUtils.extractDomain(genericProperties.domain)
            )
            writer.write(content)
            writer.close()
            fs.close()
        } catch (e: Exception) {
            throw e
        }
        artifactResourceWriter.write(ArtifactResource(
            inputStream = ArtifactInputStream(proxyJar.inputStream(), Range.full(proxyJar.length())),
            artifactName = "proxy.jar"
        ))
    }

    companion object {
        private const val FORMAT = """
bkrepo.proxy.project.id=%s
bkrepo.proxy.name=%s
bkrepo.proxy.secret.key=%s
bkrepo.proxy.cluster.name=%s
bkrepo.gateway=%s
        """
    }
}
