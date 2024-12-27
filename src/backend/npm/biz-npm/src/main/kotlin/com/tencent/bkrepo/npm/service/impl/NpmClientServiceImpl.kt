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

package com.tencent.bkrepo.npm.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.constants.ATTRIBUTE_OCTET_STREAM_SHA1
import com.tencent.bkrepo.npm.constants.CREATED
import com.tencent.bkrepo.npm.constants.HAR_FILE_EXT
import com.tencent.bkrepo.npm.constants.HSP_TYPE
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.MODIFIED
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_TGZ_FILE
import com.tencent.bkrepo.npm.constants.OHPM_ARTIFACT_TYPE
import com.tencent.bkrepo.npm.constants.OHPM_CHANGELOG_FILE_NAME
import com.tencent.bkrepo.npm.constants.OHPM_DEFAULT_ARTIFACT_TYPE
import com.tencent.bkrepo.npm.constants.OHPM_DEPRECATE
import com.tencent.bkrepo.npm.constants.OHPM_README_FILE_NAME
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.constants.SIZE
import com.tencent.bkrepo.npm.constants.TGZ_FULL_PATH_WITH_DASH_SEPARATOR
import com.tencent.bkrepo.npm.exception.NpmArtifactExistException
import com.tencent.bkrepo.npm.exception.NpmArtifactNotFoundException
import com.tencent.bkrepo.npm.exception.NpmBadRequestException
import com.tencent.bkrepo.npm.exception.NpmTagNotExistException
import com.tencent.bkrepo.npm.handler.NpmDependentHandler
import com.tencent.bkrepo.npm.handler.NpmPackageHandler
import com.tencent.bkrepo.npm.model.metadata.NpmPackageMetaData
import com.tencent.bkrepo.npm.model.metadata.NpmVersionMetadata
import com.tencent.bkrepo.npm.model.properties.PackageProperties
import com.tencent.bkrepo.npm.pojo.NpmSearchInfoMap
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
import com.tencent.bkrepo.npm.pojo.NpmSuccessResponse
import com.tencent.bkrepo.npm.pojo.enums.NpmOperationAction
import com.tencent.bkrepo.npm.pojo.enums.NpmOperationAction.UNPUBLISH
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import com.tencent.bkrepo.npm.pojo.metadata.disttags.DistTags
import com.tencent.bkrepo.npm.pojo.user.OhpmDistTagRequest
import com.tencent.bkrepo.npm.service.NpmClientService
import com.tencent.bkrepo.npm.utils.BeanUtils
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.npm.utils.TimeUtil
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.io.InputStream
import kotlin.system.measureTimeMillis

@Service
class NpmClientServiceImpl(
    private val npmDependentHandler: NpmDependentHandler,
    private val metadataClient: MetadataClient,
    private val npmPackageHandler: NpmPackageHandler
) : NpmClientService, AbstractNpmService() {

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun publishOrUpdatePackage(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        name: String
    ): NpmSuccessResponse {
        try {
            val npmPackageMetaData =
                objectMapper.readValue(HttpContextHolder.getRequest().inputStream, NpmPackageMetaData::class.java)
            when {
                isUploadRequest(npmPackageMetaData) -> {
                    measureTimeMillis {
                        handlerPackagePublish(userId, artifactInfo, npmPackageMetaData)
                    }.apply {
                        logger.info(
                            "user [$userId] public npm package [$name] " +
                                "to repo [${artifactInfo.getRepoIdentify()}] success, elapse $this ms"
                        )
                    }
                    return NpmSuccessResponse.createEntitySuccess()
                }
                isDeprecateRequest(npmPackageMetaData) -> {
                    handlerPackageDeprecated(userId, artifactInfo, npmPackageMetaData)
                    return NpmSuccessResponse.updatePkgSuccess()
                }
                else -> {
                    val message = "Unknown npm put/update request, check the debug logs for further information."
                    logger.warn(message)
                    logger.debug(
                        "Unknown npm put/update request: {}",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(npmPackageMetaData)
                    )
                    // 异常声明为npm模块的异常
                    throw NpmBadRequestException(message)
                }
            }
        } catch (exception: IOException) {
            logger.error("Exception while reading package metadata: ${exception.message}")
            throw exception
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun packageInfo(artifactInfo: NpmArtifactInfo, name: String): NpmPackageMetaData {
        with(artifactInfo) {
            logger.info("handling query package metadata request for package [$name] in repo [$projectId/$repoName]")
            return queryPackageInfo(artifactInfo, name)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun packageVersionInfo(artifactInfo: NpmArtifactInfo, name: String, version: String): NpmVersionMetadata {
        with(artifactInfo) {
            logger.info(
                "handling query package version metadata request for package [$name] " +
                    "and version [$version] in repo [$projectId/$repoName]"
            )
            if (StringUtils.equals(version, LATEST)) {
                return searchLatestVersionMetadata(artifactInfo, name)
            }
            return searchVersionMetadata(artifactInfo, name, version)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun download(artifactInfo: NpmArtifactInfo) {
        val context = ArtifactDownloadContext()
        context.putAttribute(NPM_FILE_FULL_PATH, context.artifactInfo.getArtifactFullPath())
        ArtifactContextHolder.getRepository().download(context)
    }

    @Suppress("UNCHECKED_CAST")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun search(artifactInfo: NpmArtifactInfo, searchRequest: MetadataSearchRequest): NpmSearchResponse {
        val context = ArtifactSearchContext()
        context.putAttribute(SEARCH_REQUEST, searchRequest)
        val npmSearchInfoMapList = ArtifactContextHolder.getRepository().search(context) as List<NpmSearchInfoMap>
        return NpmSearchResponse(npmSearchInfoMapList)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun getDistTags(artifactInfo: NpmArtifactInfo, name: String): DistTags {
        with(artifactInfo) {
            logger.info("handling get distTags request for package [$name] in repo [$projectId/$repoName]")
            val packageMetaData = queryPackageInfo(artifactInfo, name, false)
            return packageMetaData.distTags.getMap()
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun addDistTags(userId: String, artifactInfo: NpmArtifactInfo, name: String, tag: String) {
        logger.info(
            "handling add distTags [$tag] request for package [$name] " +
                "in repo [${artifactInfo.getRepoIdentify()}]"
        )
        val packageMetaData = queryPackageInfo(artifactInfo, name, false)
        val version = if (ArtifactContextHolder.getRepoDetail()?.type == RepositoryType.OHPM) {
            objectMapper.readValue(HttpContextHolder.getRequest().inputStream, OhpmDistTagRequest::class.java).version
        } else {
            objectMapper.readValue(HttpContextHolder.getRequest().inputStream, String::class.java)
        }
        if ((LATEST == tag && packageMetaData.versions.map.containsKey(version)) || LATEST != tag) {
            packageMetaData.distTags.getMap()[tag] = version
            doPackageFileUpload(userId, artifactInfo, packageMetaData)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun deleteDistTags(userId: String, artifactInfo: NpmArtifactInfo, name: String, tag: String) {
        logger.info(
            "handling delete distTags [$tag] request for package [$name] " +
                "in repo [${artifactInfo.getRepoIdentify()}]"
        )
        if (LATEST == tag) {
            logger.warn(
                "dist tag for [latest] with package [$name] " +
                    "in repo [${artifactInfo.getRepoIdentify()}] cannot be deleted."
            )
            return
        }
        val packageMetaData = queryPackageInfo(artifactInfo, name, false)
        packageMetaData.distTags.getMap().remove(tag)
        doPackageFileUpload(userId, artifactInfo, packageMetaData)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun updatePackage(userId: String, artifactInfo: NpmArtifactInfo, name: String) {
        logger.info("handling update package request for package [$name] in repo [${artifactInfo.getRepoIdentify()}]")
        val packageMetadata =
            objectMapper.readValue(HttpContextHolder.getRequest().inputStream, NpmPackageMetaData::class.java)
        doPackageFileUpload(userId, artifactInfo, packageMetadata)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun deleteVersion(
        artifactInfo: NpmArtifactInfo,
        name: String,
        version: String,
        tarballPath: String
    ) {
        logger.info("handling delete version [$version] request for package [$name].")
        val fullPathList = mutableListOf<String>()
        val ohpm = ArtifactContextHolder.getRepoDetail()?.type == RepositoryType.OHPM
        val packageKey = NpmUtils.packageKey(name, ohpm)
        with(artifactInfo) {
            // 判断package_version是否存在
            if (tarballPath.isEmpty() || !packageVersionExist(projectId, repoName, packageKey, version)) {
                throw NpmArtifactNotFoundException("package [$name] with version [$version] not exists.")
            }
            fullPathList.add(tarballPath)
            if (ohpm) {
                val hspPath = NpmUtils.harPathToHspPath(tarballPath)
                fullPathList.add(hspPath)
                fullPathList.add(NpmUtils.getReadmeDirFromTarballPath(tarballPath))
            }
            fullPathList.add(NpmUtils.getVersionPackageMetadataPath(name, version))
            val context = ArtifactRemoveContext()
            // 删除包管理中对应的version
            npmPackageHandler.deleteVersion(context.userId, name, version, artifactInfo)
            context.putAttribute(NPM_FILE_FULL_PATH, fullPathList)
            ArtifactContextHolder.getRepository().remove(context)
            logger.info("userId [${context.userId}] delete version [$version] for package [$name] success.")
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun deletePackage(userId: String, artifactInfo: NpmArtifactInfo, name: String) {
        logger.info("handling delete package request for package [$name]")
        val packageMetaData = queryPackageInfo(artifactInfo, name, false)
        checkOhpmDependentsAndDeprecate(userId, artifactInfo, packageMetaData, null)
        val fullPathList = mutableListOf<String>()
        fullPathList.add(".npm/$name")
        fullPathList.add(name)
        val context = ArtifactRemoveContext()
        context.putAttribute(NPM_FILE_FULL_PATH, fullPathList)
        // 删除包
        npmPackageHandler.deletePackage(userId, name, artifactInfo)
        ArtifactContextHolder.getRepository().remove(context).also {
            logger.info("userId [$userId] delete package [$name] success.")
        }
        val ohpm = context.repositoryDetail.type == RepositoryType.OHPM
        npmDependentHandler.updatePackageDependents(userId, artifactInfo, packageMetaData, UNPUBLISH, ohpm)
    }

    override fun checkOhpmDependentsAndDeprecate(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        packageMetaData: NpmPackageMetaData,
        version: String?
    ) {
        val projectId = artifactInfo.projectId
        val repoName = artifactInfo.repoName
        val name = packageMetaData.name!!
        val ohpm = ArtifactContextHolder.getRepoDetail()!!.type == RepositoryType.OHPM
        if (!ohpm || !npmDependentHandler.existsPackageDependents(projectId, repoName, name, ohpm)) {
            return
        }

        packageMetaData.versions.map.forEach {
            if (version == null || version == it.key) {
                it.value.set(OHPM_DEPRECATE, true)
            }
        }
        doPackageFileUpload(userId, artifactInfo, packageMetaData)
        throw NpmBadRequestException("The OHPM package \"${name}\" has been depended on by other components.")
    }

    private fun searchLatestVersionMetadata(artifactInfo: NpmArtifactInfo, name: String): NpmVersionMetadata {
        logger.info("handling query latest version metadata request for package [$name]")
        try {
            val context = ArtifactQueryContext()
            val packageFullPath = NpmUtils.getPackageMetadataPath(name)
            context.putAttribute(NPM_FILE_FULL_PATH, packageFullPath)
            val inputStream =
                ArtifactContextHolder.getRepository().query(context) as? InputStream
                    ?: throw NpmArtifactNotFoundException("document not found")
            val npmPackageMetaData = inputStream.use { objectMapper.readValue(it, NpmPackageMetaData::class.java) }
            val distTags = npmPackageMetaData.distTags
            if (!distTags.getMap().containsKey(LATEST)) {
                val message =
                    "the dist tag [latest] is not found in package [$name] in repo [${artifactInfo.getRepoIdentify()}]"
                logger.error(message)
                throw NpmTagNotExistException(message)
            }
            val latestVersion = distTags.getMap()[LATEST]!!
            return searchVersionMetadata(artifactInfo, name, latestVersion)
        } catch (exception: IOException) {
            val message = "Unable to get npm metadata for package $name and version latest"
            logger.error(message)
            throw NpmBadRequestException(message)
        }
    }

    private fun searchVersionMetadata(
        artifactInfo: NpmArtifactInfo,
        name: String,
        version: String
    ): NpmVersionMetadata {
        try {
            val context = ArtifactQueryContext()
            val packageFullPath = NpmUtils.getVersionPackageMetadataPath(name, version)
            context.putAttribute(NPM_FILE_FULL_PATH, packageFullPath)
            val inputStream =
                ArtifactContextHolder.getRepository().query(context) as? InputStream
                    ?: throw NpmArtifactNotFoundException("document not found")
            val versionMetadata = inputStream.use { objectMapper.readValue(it, NpmVersionMetadata::class.java) }
            modifyVersionMetadataTarball(artifactInfo, name, versionMetadata)
            return versionMetadata
        } catch (exception: IOException) {
            val message = "Unable to get npm metadata for package $name and version $version"
            logger.error(message)
            throw NpmBadRequestException(message)
        }
    }

    private fun handlerPackagePublish(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        npmPackageMetaData: NpmPackageMetaData
    ) {
        if (npmPackageMetaData.attachments == null) {
            val message = "Missing attachments with tarball data, aborting upload for '${npmPackageMetaData.name}'"
            logger.warn(message)
            throw NpmBadRequestException(message)
        }
        try {
            val size = npmPackageMetaData.attachments!!.getMap().values.iterator().next().length!!.toLong()
            val ohpm = ArtifactContextHolder.getRepoDetail()!!.type == RepositoryType.OHPM
            if (ohpm) {
                resolveOhpm(npmPackageMetaData)
            }
            handlerAttachmentsUpload(userId, artifactInfo, npmPackageMetaData)
            handlerPackageFileUpload(userId, artifactInfo, npmPackageMetaData, size, ohpm)
            handlerVersionFileUpload(userId, artifactInfo, npmPackageMetaData, size)
            npmDependentHandler.updatePackageDependents(
                userId,
                artifactInfo,
                npmPackageMetaData,
                NpmOperationAction.PUBLISH,
                ohpm
            )
            val versionMetadata = npmPackageMetaData.versions.map.values.iterator().next()
            npmPackageHandler.createVersion(userId, artifactInfo, versionMetadata, size, ohpm)
        } catch (exception: IOException) {
            val version = NpmUtils.getLatestVersionFormDistTags(npmPackageMetaData.distTags)
            logger.error(
                "userId [$userId] publish package [${npmPackageMetaData.name}] for version [$version] " +
                    "to repo [${artifactInfo.projectId}/${artifactInfo.repoName}] failed."
            )
        }
    }

    private fun resolveOhpm(npmPackageMetaData: NpmPackageMetaData) {
        npmPackageMetaData.versions.map.forEach {
            val version = it.value
            resolveOhpmHsp(version)
            val hspType = npmPackageMetaData.any()[HSP_TYPE]?.toString()
            if (!hspType.isNullOrEmpty()) {
                version.set(HSP_TYPE, hspType)
            }
            if (version.any()[OHPM_ARTIFACT_TYPE] == null) {
                // OpenHarmony包制品类型，有两个选项：original、obfuscation
                // original：源码，即发布源码(.ts/.ets)；obfuscation：混淆代码，即源码经过混淆之后发布上传
                // 默认为original
                version.set(OHPM_ARTIFACT_TYPE, OHPM_DEFAULT_ARTIFACT_TYPE)
            }
        }
    }

    private fun handlerPackageFileUpload(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        npmPackageMetaData: NpmPackageMetaData,
        size: Long,
        ohpm: Boolean,
    ) {
        with(artifactInfo) {
            val packageKey = NpmUtils.packageKeyByRepoType(npmPackageMetaData.name.orEmpty())
            val gmtTime = TimeUtil.getGMTTime()
            val npmMetadata = npmPackageMetaData.versions.map.values.iterator().next()
            if (!npmMetadata.dist!!.any().containsKey(SIZE)) {
                npmMetadata.dist!!.set(SIZE, size)
            }
            // 第一次上传
            if (!packageExist(projectId, repoName, packageKey)) {
                if (ohpm) {
                    npmPackageMetaData.rev = npmPackageMetaData.versions.map.size.toString()
                }
                npmPackageMetaData.time.add(CREATED, gmtTime)
                npmPackageMetaData.time.add(MODIFIED, gmtTime)
                npmPackageMetaData.time.add(npmMetadata.version!!, gmtTime)
                doPackageFileUpload(userId, artifactInfo, npmPackageMetaData)
                return
            }
            val originalPackageInfo = queryPackageInfo(artifactInfo, npmPackageMetaData.name!!, false)
            originalPackageInfo.versions.map.putAll(npmPackageMetaData.versions.map)
            originalPackageInfo.distTags.getMap().putAll(npmPackageMetaData.distTags.getMap())
            originalPackageInfo.time.add(MODIFIED, gmtTime)
            originalPackageInfo.time.add(npmMetadata.version!!, gmtTime)
            if (ohpm) {
                NpmUtils.updateLatestVersion(originalPackageInfo)
                originalPackageInfo.rev = originalPackageInfo.versions.map.size.toString()
            }
            doPackageFileUpload(userId, artifactInfo, originalPackageInfo)
        }
    }

    private fun doPackageFileUpload(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        npmPackageMetaData: NpmPackageMetaData
    ) {
        with(artifactInfo) {
            val fullPath = NpmUtils.getPackageMetadataPath(npmPackageMetaData.name!!)
            val inputStream = objectMapper.writeValueAsString(npmPackageMetaData).byteInputStream()
            val artifactFile = inputStream.use { ArtifactFileFactory.build(it) }
            val context = ArtifactUploadContext(artifactFile)
            context.putAttribute(NPM_FILE_FULL_PATH, fullPath)
            ArtifactContextHolder.getRepository().upload(context).also {
                logger.info(
                    "user [$userId] upload npm package metadata file [$fullPath] " +
                        "into repo [$projectId/$repoName] success."
                )
            }
            artifactFile.delete()
        }
    }

    private fun handlerVersionFileUpload(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        npmPackageMetaData: NpmPackageMetaData,
        size: Long
    ) {
        with(artifactInfo) {
            val npmMetadata = npmPackageMetaData.versions.map.values.iterator().next()
            if (!npmMetadata.dist!!.any().containsKey(SIZE)) {
                npmMetadata.dist!!.set(SIZE, size)
            }
            val fullPath = NpmUtils.getVersionPackageMetadataPath(npmMetadata.name!!, npmMetadata.version!!)
            val inputStream = objectMapper.writeValueAsString(npmMetadata).byteInputStream()
            val artifactFile = inputStream.use { ArtifactFileFactory.build(it) }
            val context = ArtifactUploadContext(artifactFile)
            context.putAttribute(NPM_FILE_FULL_PATH, fullPath)
            // ohpm包没有shasum字段
            npmMetadata.dist?.shasum?.let { context.putAttribute(ATTRIBUTE_OCTET_STREAM_SHA1, it) }
            ArtifactContextHolder.getRepository().upload(context).also {
                logger.info(
                    "user [$userId] upload npm package version metadata file [$fullPath] " +
                        "into repo [$projectId/$repoName] success."
                )
            }
            artifactFile.delete()
        }
    }

    private fun handlerAttachmentsUpload(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        npmPackageMetaData: NpmPackageMetaData
    ) {
        val versionMetadata = npmPackageMetaData.versions.map.values.iterator().next()
        val packageKey = NpmUtils.packageKeyByRepoType(versionMetadata.name.orEmpty())
        val version = versionMetadata.version.orEmpty()
        with(artifactInfo) {
            // 判断包版本是否存在 如果该版本先前发布过，也不让再次发布该版本
            if (packageVersionExist(projectId, repoName, packageKey, version) ||
                packageHistoryVersionExist(projectId, repoName, packageKey, version)) {
                throw NpmArtifactExistException(
                    "You cannot publish over the previously published versions: ${versionMetadata.version}."
                )
            }
        }
        npmPackageMetaData.attachments!!.getMap().forEach {
            val fullPath = "${versionMetadata.name}/-/${it.key}"
            handlerAttachmentsUpload(userId, artifactInfo, it.value, fullPath)
        }
        // 将attachments移除
        npmPackageMetaData.attachments = null
    }

    private fun handlerAttachmentsUpload(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        attachment: NpmPackageMetaData.Attachment,
        fullPath: String,
    ) {
        with(artifactInfo) {
            logger.info("user [$userId] deploying npm package [$fullPath] into repo [$projectId/$repoName]")
            try {
                val inputStream = tgzContentToInputStream(attachment.data!!)
                val artifactFile = inputStream.use { ArtifactFileFactory.build(it) }
                if (fullPath.endsWith(HAR_FILE_EXT)) {
                    // 保存readme,changelog文件
                    val readmeDir = NpmUtils.getReadmeDirFromTarballPath(fullPath)
                    artifactFile.getInputStream().use { handlerOhpmReadmeAndChangelogUpload(it, readmeDir) }
                }
                val context = ArtifactUploadContext(artifactFile)
                context.putAttribute(NPM_FILE_FULL_PATH, fullPath)
                context.putAttribute("attachments.content_type", attachment.contentType!!)
                context.putAttribute("attachments.length", attachment.length!!)
                context.putAttribute("name", NPM_PACKAGE_TGZ_FILE)
                // context.putAttribute(NPM_METADATA, buildProperties(versionMetadata))
                ArtifactContextHolder.getRepository().upload(context)
                artifactFile.delete()
            } catch (exception: IOException) {
                logger.error(
                    "Failed deploying npm package [$fullPath] into repo [$projectId/$repoName] due to : $exception"
                )
            }
        }
    }

    private fun handlerOhpmReadmeAndChangelogUpload(inputStream: InputStream, readmeDir: String) {
        try {
            val (readme, changelog) = NpmUtils.getReadmeAndChangeLog(inputStream)
            readme?.let { uploadReadmeOrChangeLog(it, "$readmeDir/$OHPM_README_FILE_NAME") }
            changelog?.let { uploadReadmeOrChangeLog(it, "$readmeDir/$OHPM_CHANGELOG_FILE_NAME") }
        }  catch (exception: IOException) {
            logger.error(
                "Failed deploying npm readme [$readmeDir] due to : $exception"
            )
        }
    }

    private fun uploadReadmeOrChangeLog(byteArray: ByteArray, fullPath: String) {
        val artifactFile = byteArray.inputStream().use { ArtifactFileFactory.build(it) }
        val context = ArtifactUploadContext(artifactFile)
        context.putAttribute(NPM_FILE_FULL_PATH, fullPath)
        context.putAttribute("name", NPM_PACKAGE_TGZ_FILE)
        ArtifactContextHolder.getRepository().upload(context)
        artifactFile.delete()
    }

    private fun buildProperties(npmVersionMetadata: NpmVersionMetadata?): List<MetadataModel> {
        return npmVersionMetadata?.let {
            val npmProperties = PackageProperties(
                it.license,
                it.keywords,
                it.name!!,
                it.version!!,
                it.maintainers,
                it.any()["deprecated"] as? String
            )
            BeanUtils.beanToMap(npmProperties).filterValues { value -> value != null }.map { metadata ->
                MetadataModel(key = metadata.key, value = metadata.value!!)
            }
        } ?: emptyList()
    }

    private fun tgzContentToInputStream(data: String): InputStream {
        return Base64.decodeBase64(data).inputStream()
    }

    private fun handlerPackageDeprecated(
        userId: String,
        artifactInfo: NpmArtifactInfo,
        npmPackageMetaData: NpmPackageMetaData
    ) {
        logger.info(
            "userId [$userId] handler deprecated request: [$npmPackageMetaData] " +
                "in repo [${artifactInfo.projectId}]"
        )
        doPackageFileUpload(userId, artifactInfo, npmPackageMetaData)
        // 元数据增加过期信息
        val iterator = npmPackageMetaData.versions.map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val pathWithDash = entry.value.dist?.tarball?.substringAfter(npmPackageMetaData.name!!)
                ?.contains(TGZ_FULL_PATH_WITH_DASH_SEPARATOR) ?: true
            val tgzFullPath = NpmUtils.getTarballPathByRepoType(npmPackageMetaData.name!!, entry.key, pathWithDash)
            if (entry.value.any().containsKey("deprecated")) {
                metadataClient.saveMetadata(
                    MetadataSaveRequest(
                        projectId = artifactInfo.projectId,
                        repoName = artifactInfo.repoName,
                        fullPath = tgzFullPath,
                        nodeMetadata = buildProperties(entry.value),
                        operator = userId
                    )
                )
            }
        }
    }

    companion object {

        fun isUploadRequest(npmPackageMetaData: NpmPackageMetaData): Boolean {
            val attachments = npmPackageMetaData.attachments
            return attachments != null && attachments.getMap().entries.isNotEmpty()
        }

        fun isDeprecateRequest(npmPackageMetaData: NpmPackageMetaData): Boolean {
            val versions = npmPackageMetaData.versions
            val iterator = versions.map.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val npmMetadata = entry.value
                if (npmMetadata.any().containsKey("deprecated")) {
                    return true
                }
            }

            return false
        }

        private val logger: Logger = LoggerFactory.getLogger(NpmClientServiceImpl::class.java)
    }
}
