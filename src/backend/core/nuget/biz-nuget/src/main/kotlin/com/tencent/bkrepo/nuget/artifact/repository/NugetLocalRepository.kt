/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.nuget.artifact.repository

import com.fasterxml.jackson.core.JsonProcessingException
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.constant.MANIFEST
import com.tencent.bkrepo.nuget.constant.NugetMessageCode
import com.tencent.bkrepo.nuget.constant.NugetQueryType
import com.tencent.bkrepo.nuget.constant.PACKAGE_NAME
import com.tencent.bkrepo.nuget.constant.QUERY_TYPE
import com.tencent.bkrepo.nuget.constant.REGISTRATION_PATH
import com.tencent.bkrepo.nuget.handler.NugetPackageHandler
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDeleteArtifactInfo
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo
import com.tencent.bkrepo.nuget.pojo.artifact.NugetPublishArtifactInfo
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Feed
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationIndex
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage
import com.tencent.bkrepo.nuget.util.DecompressUtil.resolverNuspecMetadata
import com.tencent.bkrepo.nuget.util.NugetUtils
import com.tencent.bkrepo.nuget.util.NugetV3RegistrationUtils
import com.tencent.bkrepo.nuget.util.NugetVersionUtils
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.streams.toList

@Suppress("TooManyFunctions")
@Component
class NugetLocalRepository(
    private val nugetPackageHandler: NugetPackageHandler
) : LocalRepository() {

    override fun query(context: ArtifactQueryContext): Any? {
        return when(context.getAttribute<NugetQueryType>(QUERY_TYPE)!!) {
            NugetQueryType.PACKAGE_VERSIONS -> enumerateVersions(context, context.getStringAttribute(PACKAGE_NAME)!!)
            NugetQueryType.SERVICE_INDEX -> feed(context.artifactInfo as NugetArtifactInfo)
            NugetQueryType.REGISTRATION_INDEX -> registrationIndex(context)
            NugetQueryType.REGISTRATION_PAGE -> registrationPage(context)
            NugetQueryType.REGISTRATION_LEAF -> registrationLeaf(context)
        }
    }

    private fun enumerateVersions(context: ArtifactQueryContext, packageId: String): List<String>? {
        return packageService.listExistPackageVersion(
            context.projectId, context.repoName, PackageKeys.ofNuget(packageId), emptyList()
        ).takeIf { it.isNotEmpty() }
    }

    private fun feed(artifactInfo: NugetArtifactInfo): Feed {
        return NugetUtils.renderServiceIndex(artifactInfo)
    }

    private fun registrationIndex(context: ArtifactQueryContext): RegistrationIndex? {
        with(context) {
            val packageName = (artifactInfo as NugetRegistrationArtifactInfo).packageName
            val registrationPath = context.getStringAttribute(REGISTRATION_PATH)
            val packageVersionList = packageService.listAllVersion(
                projectId = projectId,
                repoName = repoName,
                packageKey = PackageKeys.ofNuget(packageName),
                option = VersionListOption()
            )
            if (packageVersionList.isEmpty()) return null
            val sortedVersionList = packageVersionList.stream().sorted { o1, o2 ->
                NugetVersionUtils.compareSemVer(o1.name, o2.name)
            }.toList()
            try {
                val v3RegistrationUrl = NugetUtils.getV3Url(artifactInfo) + '/' + registrationPath
                return NugetV3RegistrationUtils.metadataToRegistrationIndex(sortedVersionList, v3RegistrationUrl)
            } catch (ignored: JsonProcessingException) {
                logger.error("failed to deserialize metadata to registration index json")
                throw ignored
            }
        }
    }

    private fun registrationPage(context: ArtifactQueryContext): RegistrationPage? {
        with(context) {
            val nugetArtifactInfo = artifactInfo as NugetRegistrationArtifactInfo
            val registrationPath = context.getStringAttribute(REGISTRATION_PATH)
            val packageKey = PackageKeys.ofNuget(nugetArtifactInfo.packageName)
            val packageVersionList = packageService.listAllVersion(projectId, repoName, packageKey, VersionListOption())
            if (packageVersionList.isEmpty()) return null
            val sortedVersionList = packageVersionList.stream().sorted { o1, o2 ->
                NugetVersionUtils.compareSemVer(o1.name, o2.name)
            }.toList()
            try {
                val v3RegistrationUrl = NugetUtils.getV3Url(artifactInfo) + '/' + registrationPath
                return NugetV3RegistrationUtils.metadataToRegistrationPage(
                    sortedVersionList,
                    nugetArtifactInfo.packageName,
                    nugetArtifactInfo.lowerVersion,
                    nugetArtifactInfo.upperVersion,
                    v3RegistrationUrl
                )
            } catch (ignored: JsonProcessingException) {
                logger.error("failed to deserialize metadata to registration index json")
                throw ignored
            }
        }
    }

    private fun registrationLeaf(context: ArtifactQueryContext): RegistrationLeaf? {
        with(context) {
            val registrationPath = context.getStringAttribute(REGISTRATION_PATH)
            val nugetArtifactInfo = artifactInfo as NugetRegistrationArtifactInfo
            val packageKey = PackageKeys.ofNuget(nugetArtifactInfo.packageName)
            // 确保version一定存在
            packageService.findVersionByName(projectId, repoName, packageKey, nugetArtifactInfo.version) ?: return null
            try {
                val v3RegistrationUrl = NugetUtils.getV3Url(artifactInfo) + '/' + registrationPath
                return NugetV3RegistrationUtils.metadataToRegistrationLeaf(
                    nugetArtifactInfo.packageName, nugetArtifactInfo.version, true, v3RegistrationUrl
                )
            } catch (ignored: JsonProcessingException) {
                logger.error("failed to deserialize metadata to registration index json")
                throw ignored
            }
        }
    }

    override fun onUploadBefore(context: ArtifactUploadContext) {
        super.onUploadBefore(context)
        // 校验版本是否存在，存在则冲突
        with(context.artifactInfo as NugetPublishArtifactInfo) {
            packageService.findVersionByName(
                projectId, repoName, PackageKeys.ofNuget(packageName.toLowerCase()), version
            )?.let {
                throw ErrorCodeException(
                    messageCode = NugetMessageCode.VERSION_EXISTED,
                    params = arrayOf(version),
                    status = HttpStatus.CONFLICT
                )
            }
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        with(context.artifactInfo as NugetPublishArtifactInfo) {
            uploadNupkg(context)
            nugetPackageHandler.createPackageVersion(context)
            context.response.status = HttpStatus.CREATED.value
            context.response.writer.write("Successfully published NuPkg to: ${getArtifactFullPath()}")
        }
    }

    /**
     * 保存nupkg 文件内容
     */
    private fun uploadNupkg(context: ArtifactUploadContext) {
        val request = buildNodeCreateRequest(context).copy(overwrite = true)
        storageManager.storeArtifactFile(request, context.getArtifactFile(), context.storageCredentials)
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        // download package manifest
        val nugetArtifactInfo = context.artifactInfo as NugetDownloadArtifactInfo
        return if (nugetArtifactInfo.type == MANIFEST) {
            onDownloadManifest(context)
        } else {
            super.onDownload(context)
        }
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context.artifactInfo as NugetDownloadArtifactInfo) {
            return if (type != MANIFEST) {
                PackageDownloadRecord(projectId, repoName, PackageKeys.ofNuget(packageName), version)
            } else null
        }
    }

    /**
     * 版本不存在时 status code 404
     */
    override fun remove(context: ArtifactRemoveContext) {
        with(context.artifactInfo as NugetDeleteArtifactInfo) {
            if (version.isNotBlank()) {
                packageService.findVersionByName(projectId, repoName, packageName, version)?.let {
                    removeVersion(this, it, context.userId)
                } ?: throw VersionNotFoundException(version)
            } else {
                packageService.listAllVersion(projectId, repoName, packageName, VersionListOption())
                    .takeUnless { it.isEmpty() }
                    ?.forEach { removeVersion(this, it, context.userId) }
                    ?: throw PackageNotFoundException(packageName)
            }
        }
    }

    /**
     * 删除[version] 对应的node节点也会一起删除
     */
    private fun removeVersion(artifactInfo: NugetDeleteArtifactInfo, version: PackageVersion, userId: String) {
        with(artifactInfo) {
            packageService.deleteVersion(
                projectId,
                repoName,
                packageName,
                version.name,
                HttpContextHolder.getClientAddress()
            )
            val nugetPath = version.contentPath.orEmpty()
            if (nugetPath.isNotBlank()) {
                val request = NodeDeleteRequest(projectId, repoName, nugetPath, userId)
                nodeService.deleteNode(request)
            }
        }
    }

    private fun onDownloadManifest(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val nugetArtifactInfo = artifactInfo as NugetDownloadArtifactInfo
            val nuspecFullPath = NugetUtils.getNuspecFullPath(nugetArtifactInfo.packageName, nugetArtifactInfo.version)
            val nuspecNode = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, nuspecFullPath))
            val inputStream = if (nuspecNode != null) {
                storageManager.loadArtifactInputStream(nuspecNode, storageCredentials)
            } else {
                val nupkgFullPath =
                    NugetUtils.getNupkgFullPath(nugetArtifactInfo.packageName, nugetArtifactInfo.version)
                val nupkgNode = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, nupkgFullPath))
                storageManager.loadArtifactInputStream(nupkgNode, storageCredentials)?.use {
                    it.resolverNuspecMetadata().byteInputStream()
                }
            } ?: return null
            val responseName = artifactInfo.getResponseName()
            val artifactFile = ArtifactFileFactory.build(inputStream)
            val size = artifactFile.getSize()
            val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
            val artifactResource = ArtifactResource(
                artifactStream, responseName, nuspecNode, ArtifactChannel.LOCAL, useDisposition
            )
            // 临时文件删除
            artifactFile.delete()
            artifactResource.contentType = MediaTypes.APPLICATION_XML
            return artifactResource
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NugetLocalRepository::class.java)
    }
}
