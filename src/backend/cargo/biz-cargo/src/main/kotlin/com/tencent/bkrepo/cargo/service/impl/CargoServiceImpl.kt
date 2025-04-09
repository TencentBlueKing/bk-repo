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

package com.tencent.bkrepo.cargo.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.cargo.config.CargoProperties
import com.tencent.bkrepo.cargo.constants.CARGO_INDEX_PREFIX
import com.tencent.bkrepo.cargo.constants.CRATE_CONFIG
import com.tencent.bkrepo.cargo.constants.CRATE_DOWNLOAD_URL_SUFFIX
import com.tencent.bkrepo.cargo.constants.CRATE_FILE
import com.tencent.bkrepo.cargo.constants.CRATE_INDEX
import com.tencent.bkrepo.cargo.constants.CRATE_NAME
import com.tencent.bkrepo.cargo.constants.CRATE_VERSION
import com.tencent.bkrepo.cargo.constants.CargoMessageCode
import com.tencent.bkrepo.cargo.constants.FILE_TYPE
import com.tencent.bkrepo.cargo.constants.PAGE_SIZE
import com.tencent.bkrepo.cargo.constants.QUERY
import com.tencent.bkrepo.cargo.constants.YANKED
import com.tencent.bkrepo.cargo.exception.CargoFileNotFoundException
import com.tencent.bkrepo.cargo.listener.event.CargoPackageYankEvent
import com.tencent.bkrepo.cargo.pojo.CargoSearchResult
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo
import com.tencent.bkrepo.cargo.pojo.index.IndexConfiguration
import com.tencent.bkrepo.cargo.service.CargoService
import com.tencent.bkrepo.cargo.utils.CargoUtils.getCargoFileFullPath
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes.TEXT_PLAIN
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import org.springframework.stereotype.Service

@Service
class CargoServiceImpl(
    private val cargoProperties: CargoProperties,
    private val permissionManager: PermissionManager,
    private val commonService: CommonService
) : CargoService {


    override fun getIndexOfCrate(cargoArtifactInfo: CargoArtifactInfo) {
        if (cargoArtifactInfo.getArtifactFullPath().endsWith(CRATE_CONFIG)) {
            val response = HttpContextHolder.getResponse()
            response.contentType = TEXT_PLAIN
            response.status = HttpStatus.OK.value
            response.writer.println(getIndexConfiguration(cargoArtifactInfo).toJsonString())

        } else {
            ArtifactContextHolder.getRepoDetail()
            permissionManager.checkRepoPermission(
                PermissionAction.READ, cargoArtifactInfo.projectId, cargoArtifactInfo.repoName
            )
            val context = ArtifactDownloadContext()
            val fullPath = CARGO_INDEX_PREFIX.removeSuffix(StringPool.SLASH) + cargoArtifactInfo.getArtifactFullPath()
            cargoArtifactInfo.setArtifactMappingUri(fullPath)
            context.putAttribute(FILE_TYPE, CRATE_INDEX)
            ArtifactContextHolder.getRepository().download(context)
        }
    }


    override fun uploadFile(cargoArtifactInfo: CargoArtifactInfo, artifactFile: ArtifactFile) {
        val context = ArtifactUploadContext(artifactFile)
        ArtifactContextHolder.getRepository().upload(context)
    }

    override fun downloadFile(cargoArtifactInfo: CargoArtifactInfo) {
        validParams(cargoArtifactInfo)
        val context = ArtifactDownloadContext()
        val fullPath = getCargoFileFullPath(cargoArtifactInfo.crateName!!, cargoArtifactInfo.crateVersion!!)
        context.artifactInfo.setArtifactMappingUri(fullPath)
        context.putAttribute(FILE_TYPE, CRATE_FILE)
        context.putAttribute(CRATE_NAME, cargoArtifactInfo.crateName)
        context.putAttribute(CRATE_VERSION, cargoArtifactInfo.crateVersion)
        ArtifactContextHolder.getRepository().download(context)
    }

    override fun yank(cargoArtifactInfo: CargoArtifactInfo) {
        doYankOperation(cargoArtifactInfo, true)
    }


    override fun unYank(cargoArtifactInfo: CargoArtifactInfo) {
        doYankOperation(cargoArtifactInfo, false)
    }

    override fun search(cargoArtifactInfo: CargoArtifactInfo, q: String, perPage: Int): CargoSearchResult {
        ArtifactContextHolder.getRepoDetail()
        permissionManager.checkRepoPermission(
            PermissionAction.READ, cargoArtifactInfo.projectId, cargoArtifactInfo.repoName
        )
        val context = ArtifactQueryContext()
        context.putAttribute(QUERY, q)
        context.putAttribute(PAGE_SIZE, perPage)
        return ArtifactContextHolder.getRepository().query(context) as CargoSearchResult
    }

    private fun doYankOperation(cargoArtifactInfo: CargoArtifactInfo, yanked: Boolean) {
        with(cargoArtifactInfo) {
            validParams(cargoArtifactInfo)
            val crateFilePath = getCargoFileFullPath(crateName!!, crateVersion!!)
            commonService.getNodeDetail(projectId, repoName, crateFilePath)
                ?: throw CargoFileNotFoundException(
                    CargoMessageCode.CARGO_FILE_NOT_FOUND, crateFilePath, "$projectId|$repoName"
                )
            val metadata = mutableMapOf<String, Any>(YANKED to yanked)
            commonService.updateNodeMetaData(
                projectId, repoName, crateFilePath, metadata
            )
            publishEvent(
                CargoPackageYankEvent(
                    ObjectBuilderUtil.buildCargoPackageYankRequest(cargoArtifactInfo, yanked)
                )
            )
        }
    }

    private fun validParams(cargoArtifactInfo: CargoArtifactInfo) {
        if (cargoArtifactInfo.crateName.isNullOrEmpty() || cargoArtifactInfo.crateVersion.isNullOrEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, listOf("crate_name", "version"))
        }

    }

    private fun getIndexConfiguration(cargoArtifactInfo: CargoArtifactInfo): IndexConfiguration {
        val repoPath = StringPool.SLASH + cargoArtifactInfo.projectId + StringPool.SLASH + cargoArtifactInfo.repoName
        val prefix = cargoProperties.domain + repoPath
        return IndexConfiguration(
            dl = prefix + CRATE_DOWNLOAD_URL_SUFFIX,
            api = prefix,
            authRequired = cargoProperties.authRequired
        )
    }
}