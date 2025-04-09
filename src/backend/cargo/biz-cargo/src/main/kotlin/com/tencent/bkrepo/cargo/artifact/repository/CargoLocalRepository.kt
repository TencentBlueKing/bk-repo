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

package com.tencent.bkrepo.cargo.artifact.repository

import com.tencent.bkrepo.cargo.constants.CARGO_CRATE_FILE
import com.tencent.bkrepo.cargo.constants.CARGO_METADATA
import com.tencent.bkrepo.cargo.constants.CargoMessageCode
import com.tencent.bkrepo.cargo.constants.DESCRIPTION
import com.tencent.bkrepo.cargo.constants.FILE_SHA256
import com.tencent.bkrepo.cargo.constants.FILE_SIZE
import com.tencent.bkrepo.cargo.constants.LATEST
import com.tencent.bkrepo.cargo.constants.NAME
import com.tencent.bkrepo.cargo.constants.PAGE_SIZE
import com.tencent.bkrepo.cargo.constants.QUERY
import com.tencent.bkrepo.cargo.constants.YANKED
import com.tencent.bkrepo.cargo.exception.CargoBadRequestException
import com.tencent.bkrepo.cargo.exception.CargoYankedException
import com.tencent.bkrepo.cargo.listener.event.CargoPackageDeleteEvent
import com.tencent.bkrepo.cargo.listener.event.CargoPackageUploadEvent
import com.tencent.bkrepo.cargo.pojo.CargoSearchResult
import com.tencent.bkrepo.cargo.pojo.CargoSuccessResponse
import com.tencent.bkrepo.cargo.pojo.CratesDetail
import com.tencent.bkrepo.cargo.pojo.SearchMeta
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo
import com.tencent.bkrepo.cargo.pojo.artifact.CargoDeleteArtifactInfo
import com.tencent.bkrepo.cargo.pojo.base.CargoMetadata
import com.tencent.bkrepo.cargo.pojo.event.CargoPackageDeleteRequest
import com.tencent.bkrepo.cargo.service.impl.CommonService
import com.tencent.bkrepo.cargo.utils.CargoUtils.getCargoFileFullPath
import com.tencent.bkrepo.cargo.utils.CargoUtils.getCargoJsonFullPath
import com.tencent.bkrepo.cargo.utils.CargoUtils.isValidPackageName
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil.buildCrateIndexData
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil.buildCrateJsonData
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil.convertToMetadata
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.repository.constant.FULL_PATH
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.search.PackageQueryBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

@Component
class CargoLocalRepository(
    private val commonService: CommonService,
) : LocalRepository() {


    override fun onUploadBefore(context: ArtifactUploadContext) {
        super.onUploadBefore(context)
        // 从上传文件中解析出元数据以及实际crate文件
        parseCargoUploadData(context)
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val nodeCreateRequest = buildNodeCreateRequest(context)
        val artifactFile = context.getAttribute(CARGO_CRATE_FILE) as ArtifactFile?
        storageManager.storeArtifactFile(nodeCreateRequest, artifactFile!!, context.storageCredentials)
    }

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        with(context) {
            val fullPath = getStringAttribute(FULL_PATH)!!
            val artifactFile = getAttribute(CARGO_CRATE_FILE) as ArtifactFile?
            val cargoMetadata = getAttribute(CARGO_METADATA) as CargoMetadata?
            logger.info("File $fullPath will be stored in $projectId|$repoName")
            return NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = fullPath,
                size = artifactFile!!.getSize(),
                sha256 = artifactFile.getFileSha256(),
                md5 = artifactFile.getFileMd5(),
                operator = userId,
                overwrite = true,
                nodeMetadata = convertToMetadata(cargoMetadata!!)
            )
        }
    }

    /**
     * 上传成功回调
     */
    override fun onUploadSuccess(context: ArtifactUploadContext) {
        with(context) {
            super.onUploadSuccess(context)
            val cargoMetadata = getAttribute(CARGO_METADATA) as CargoMetadata?
            // 存储crate json
            createAndStoreJson(context, cargoMetadata!!)
            // 创建package
            initPackageInfo(context, cargoMetadata)
            // 异步更新index
            val cksum = getStringAttribute(FILE_SHA256)!!
            val indexData = buildCrateIndexData(cargoMetadata, cksum)
            publishEvent(
                CargoPackageUploadEvent(
                    ObjectBuilderUtil.buildCargoUploadRequest(context, indexData)
                )
            )
            response.status = HttpStatus.CREATED.value
            response.writer.println(CargoSuccessResponse(true, "Crate published successfully").toJsonString())
            response.writer.flush()
        }
    }

    /**
     * 当本地文件上传后/或从远程代理下载后，创建或更新包/包版本信息
     */
    fun initPackageInfo(context: ArtifactContext, cargoMetadata: CargoMetadata) {
        with(context) {
            logger.info("start to create cargo package info.")
            val size = getLongAttribute(FILE_SIZE)!!
            val fullPath = getStringAttribute(FULL_PATH)!!
            val packageVersionCreateRequest = ObjectBuilderUtil.buildPackageVersionCreateRequest(
                userId = userId,
                projectId = projectId,
                repoName = repoName,
                cargoMetadata = cargoMetadata,
                size = size,
                fullPath = fullPath,
                metadataList = convertToMetadata(cargoMetadata)
            )
            packageService.createPackageVersion(packageVersionCreateRequest).apply {
                logger.info("user: [$userId] create package version [$packageVersionCreateRequest] success!")
            }
        }
    }

    private fun createAndStoreJson(context: ArtifactUploadContext, cargoMetadata: CargoMetadata) {
        with(context) {
            val crateJsonData = buildCrateJsonData(cargoMetadata).toJsonString()
            val jsonFullPath = getCargoJsonFullPath(cargoMetadata.name, cargoMetadata.vers)
            val jsonFile = ArtifactFileFactory.build(crateJsonData.byteInputStream())
            val nodeCreateRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = jsonFullPath,
                size = jsonFile.getSize(),
                sha256 = jsonFile.getFileSha256(),
                md5 = jsonFile.getFileMd5(),
                operator = userId,
                overwrite = true,
            )
            storageManager.storeArtifactFile(nodeCreateRequest, jsonFile, context.storageCredentials)
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context.artifactInfo as CargoArtifactInfo) {
            val fullPath = getArtifactFullPath()
            logger.info("File $fullPath will be downloaded in repo $projectId|$repoName")
            val node = nodeService.getNodeDetail(ArtifactInfo(context.projectId, context.repoName, fullPath))
            node?.let {
                if (yankedStatusCheck(node)) {
                    throw CargoYankedException(CargoMessageCode.CARGO_FILE_YANKED, node.name, "$projectId|$repoName")
                }
                context.artifactInfo.setArtifactMappingUri(node.fullPath)
                downloadIntercept(context, node)
//                packageVersion(context, node)?.let { packageVersion -> downloadIntercept(context, packageVersion) }
            }
            val inputStream = storageManager.loadArtifactInputStream(node, context.storageCredentials)
            inputStream?.let {
                return ArtifactResource(
                    inputStream,
                    context.artifactInfo.getResponseName(),
                    node,
                    ArtifactChannel.LOCAL,
                    context.useDisposition
                )
            }
            return null
        }
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context.artifactInfo as CargoArtifactInfo) {
            if (crateName.isNullOrEmpty() || crateVersion.isNullOrEmpty()) {
                return null
            }
            return PackageDownloadRecord(
                context.projectId, context.repoName, PackageKeys.ofCargo(crateName), crateVersion
            )
        }

    }

    override fun remove(context: ArtifactRemoveContext) {
        commonService.removeCargoRelatedNode(context)
        with(context.artifactInfo as CargoDeleteArtifactInfo) {
            val event = CargoPackageDeleteEvent(
                CargoPackageDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    name = PackageKeys.resolveCargo(packageName),
                    userId = context.userId,
                    version = version.ifEmpty { null }
                )
            )
            publishEvent(event)
        }
    }

    override fun query(context: ArtifactQueryContext): Any? {
        with(context.artifactInfo as CargoArtifactInfo) {
            val q = context.getStringAttribute(QUERY)
            val perPage = context.getIntegerAttribute(PAGE_SIZE) ?: 10
            val queryModel = PackageQueryBuilder().select(NAME, DESCRIPTION, LATEST)
                .projectId(projectId).repoName(repoName).sortByAsc(NAME)
                .page(0, perPage)
            if (!q.isNullOrEmpty()) {
                queryModel.name("*$q*", com.tencent.bkrepo.common.query.enums.OperationType.MATCH)
            }
            val queryResult = packageService.searchPackage(queryModel.build())
            val crates: MutableList<CratesDetail> = mutableListOf()
            queryResult.records.forEach { map ->
                crates.add(
                    CratesDetail(
                        name = map[NAME] as String,
                        description = map[DESCRIPTION] as String?,
                        maxVersion = map[LATEST] as String,
                    )
                )
            }
            if (crates.isEmpty()) {
                return null
            } else {
                return CargoSearchResult(
                    crates = crates,
                    meta = SearchMeta(queryResult.totalRecords)
                )
            }
        }
    }


    /**
     * The body of the data sent by Cargo is:
     *
     * 32-bit unsigned little-endian integer of the length of JSON data.
     * Metadata of the package as a JSON object.
     * 32-bit unsigned little-endian integer of the length of the .crate file.
     * The .crate file.
     */
    private fun parseCargoUploadData(context: ArtifactUploadContext) {
        try {
            context.getArtifactFile().getInputStream().use {
                DataInputStream(it).use { dateInputStream ->
                    println(dateInputStream.toJsonString())
                    val jsonLength = Integer.toUnsignedLong(readLittleEndianInt(dateInputStream))
                    val jsonData = ByteArray(jsonLength.toInt())
                    dateInputStream.readFully(jsonData)
                    val jsonDataStr = jsonData.toString(StandardCharsets.UTF_8)
                    val metadata = JsonUtils.objectMapper.readValue(jsonDataStr, CargoMetadata::class.java)
                    val crateLength = Integer.toUnsignedLong(readLittleEndianInt(dateInputStream))
                    val crateData = ByteArray(crateLength.toInt())
                    dateInputStream.readFully(crateData)
                    val artifactFile = ArtifactFileFactory.build(crateData.inputStream())
                    isValidPackageName(metadata.name)
                    context.putAttribute(CARGO_METADATA, metadata)
                    context.putAttribute(CARGO_CRATE_FILE, artifactFile)
                    val fullPath = getCargoFileFullPath(metadata.name, metadata.vers)
                    context.putAttribute(FULL_PATH, fullPath)
                    context.putAttribute(FILE_SIZE, artifactFile.getSize())
                    context.putAttribute(FILE_SHA256, artifactFile.getFileSha256())
                }
            }

        } catch (e: Exception) {
            logger.warn("parse cargo upload data error: ${e.message}")
            throw CargoBadRequestException(CargoMessageCode.CARGO_UPLOAD_DATA_BROKEN)
        }
    }

    private fun readLittleEndianInt(inputStream: InputStream): Int {
        val bytes = ByteArray(4)
        inputStream.read(bytes)
        return (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
    }

    private fun yankedStatusCheck(nodeDetail: NodeDetail): Boolean {
        val yanked = nodeDetail.nodeMetadata.firstOrNull { it.key == YANKED }?.value as Boolean?
        return yanked ?: false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CargoLocalRepository::class.java)
    }
}
