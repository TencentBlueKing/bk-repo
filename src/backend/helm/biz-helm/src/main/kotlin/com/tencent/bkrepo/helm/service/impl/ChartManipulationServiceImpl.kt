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

package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.helm.handler.HelmPackageHandler
import com.tencent.bkrepo.helm.constants.CHART
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.PROV
import com.tencent.bkrepo.helm.constants.SIZE
import com.tencent.bkrepo.helm.exception.HelmErrorInvalidProvenanceFileException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.listener.event.ChartDeleteEvent
import com.tencent.bkrepo.helm.listener.event.ChartVersionDeleteEvent
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.artifact.HelmDeleteArtifactInfo
import com.tencent.bkrepo.helm.pojo.chart.ChartPackageDeleteRequest
import com.tencent.bkrepo.helm.pojo.chart.ChartVersionDeleteRequest
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.service.ChartManipulationService
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import com.tencent.bkrepo.helm.utils.HelmUtils.getChartFileFullPath
import com.tencent.bkrepo.helm.utils.HelmUtils.getProvFileFullPath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChartManipulationServiceImpl(
    private val helmPackageHandler: HelmPackageHandler
) : AbstractChartService(), ChartManipulationService {

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap) {
        val keys = artifactFileMap.keys
        checkRepositoryExist(artifactInfo)
        check(keys.contains(CHART) || keys.contains(PROV)) {
            throw HelmFileNotFoundException(
                "no package or provenance file found in form fields chart and prov"
            )
        }
        if (keys.contains(CHART)) {
            val artifactFile = artifactFileMap[CHART]!!
            val context = ArtifactUploadContext(artifactFile)
            val chartMetadata = parseChartFileInfo(artifactFileMap)
            context.putAttribute(FULL_PATH, getChartFileFullPath(chartMetadata.name, chartMetadata.version))
            ArtifactContextHolder.getRepository().upload(context)

            // create package
            helmPackageHandler.createVersion(
                context.userId,
                artifactInfo,
                chartMetadata,
                context.getLongAttribute(SIZE)!!,
                context.getBooleanAttribute("isOverwrite") ?: false
            )
        }
        if (keys.contains(PROV)) {
            val context = ArtifactUploadContext(artifactFileMap[PROV]!!)
            val provFileInfo = parseProvFileInfo(artifactFileMap)
            context.putAttribute(FULL_PATH, getProvFileFullPath(provFileInfo.first, provFileInfo.second))
            ArtifactContextHolder.getRepository().upload(context)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun uploadProv(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap) {
        checkRepositoryExist(artifactInfo)
        check(artifactFileMap.keys.contains(PROV)) {
            throw HelmFileNotFoundException("no provenance file found in form fields prov")
        }
        val context = ArtifactUploadContext(artifactFileMap)
        val provFileInfo = parseProvFileInfo(artifactFileMap)
        context.putAttribute(FULL_PATH, getProvFileFullPath(provFileInfo.first, provFileInfo.second))
        ArtifactContextHolder.getRepository().upload(context)
    }

    fun parseChartFileInfo(artifactFileMap: ArtifactFileMap): HelmChartMetadata {
        val inputStream = (artifactFileMap[CHART] as MultipartArtifactFile).getInputStream()
        val result = inputStream.getArchivesContent(CHART_PACKAGE_FILE_EXTENSION)
        return result.byteInputStream().readYamlString()
    }

    fun parseProvFileInfo(artifactFileMap: ArtifactFileMap): Pair<String, String> {
        val inputStream = (artifactFileMap[PROV] as MultipartArtifactFile).getInputStream()
        val contentStr = String(inputStream.readBytes())
        val hasPGPBegin = contentStr.startsWith("-----BEGIN PGP SIGNED MESSAGE-----")
        val nameMatch = Regex("\nname:[ *](.+)").findAll(contentStr).toList().flatMap(MatchResult::groupValues)
        val versionMatch = Regex("\nversion:[ *](.+)").findAll(contentStr).toList().flatMap(MatchResult::groupValues)
        if (!hasPGPBegin || nameMatch.size != 2 || versionMatch.size != 2) {
            throw HelmErrorInvalidProvenanceFileException("invalid provenance file")
        }
        return Pair(nameMatch[1], versionMatch[1])
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteVersion(userId: String, artifactInfo: HelmDeleteArtifactInfo) {
        logger.info("handling delete chart version request: [$artifactInfo]")
        with(artifactInfo) {
            if (!packageVersionExist(projectId, repoName, packageName, version)) {
                throw HelmFileNotFoundException("remove package $packageName failed: no such file or directory")
            }
            repository.remove(ArtifactRemoveContext())
            publishEvent(ChartVersionDeleteEvent(
                ChartVersionDeleteRequest(projectId, repoName, PackageKeys.resolveHelm(packageName), version, userId)
            ))
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun deletePackage(userId: String, artifactInfo: HelmDeleteArtifactInfo) {
        logger.info("handling delete chart request: [$artifactInfo]")
        with(artifactInfo) {
            if (!packageExist(projectId, repoName, packageName)) {
                throw HelmFileNotFoundException("remove package $packageName failed: no such file or directory")
            }
            repository.remove(ArtifactRemoveContext())
            publishEvent(ChartDeleteEvent(
                ChartPackageDeleteRequest(projectId, repoName, PackageKeys.resolveHelm(packageName), userId)
            ))
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ChartManipulationServiceImpl::class.java)
    }
}
