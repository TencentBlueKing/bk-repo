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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.npm.service

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.constant.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.async.NpmDependentHandler
import com.tencent.bkrepo.npm.async.PackageHandler
import com.tencent.bkrepo.npm.constants.APPLICATION_OCTET_STEAM
import com.tencent.bkrepo.npm.constants.ATTACHMENTS
import com.tencent.bkrepo.npm.constants.ATTRIBUTE_OCTET_STREAM_SHA1
import com.tencent.bkrepo.npm.constants.CONTENT_TYPE
import com.tencent.bkrepo.npm.constants.CREATED
import com.tencent.bkrepo.npm.constants.DATA
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.DISTTAGS
import com.tencent.bkrepo.npm.constants.ERROR_MAP
import com.tencent.bkrepo.npm.constants.FILE_DASH
import com.tencent.bkrepo.npm.constants.FILE_SUFFIX
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.LENGTH
import com.tencent.bkrepo.npm.constants.MODIFIED
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_METADATA
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_JSON_FILE
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_TGZ_FILE
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_VERSION_JSON_FILE
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_JSON_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_JSON_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.REV
import com.tencent.bkrepo.npm.constants.REV_VALUE
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.constants.SHASUM
import com.tencent.bkrepo.npm.constants.TIME
import com.tencent.bkrepo.npm.constants.VERSION
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.exception.NpmArtifactExistException
import com.tencent.bkrepo.npm.exception.NpmArtifactNotFoundException
import com.tencent.bkrepo.npm.pojo.NpmDeleteResponse
import com.tencent.bkrepo.npm.pojo.NpmMetaData
import com.tencent.bkrepo.npm.pojo.NpmSearchInfoMap
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
import com.tencent.bkrepo.npm.pojo.NpmSuccessResponse
import com.tencent.bkrepo.npm.pojo.enums.NpmOperationAction
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import com.tencent.bkrepo.npm.utils.BeanUtils
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.npm.utils.TimeUtil
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NpmService @Autowired constructor(
    private val npmDependentHandler: NpmDependentHandler,
    private val metadataClient: MetadataClient,
    private val packageHandler: PackageHandler
) {

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun publish(userId: String, artifactInfo: NpmArtifactInfo, body: String): NpmSuccessResponse {
        body.takeIf { StringUtils.isNotBlank(it) } ?: throw ArtifactNotFoundException("request body not found!")
        val jsonObj = JsonParser.parseString(body).asJsonObject
        val artifactFileMap = ArtifactFileMap()
        return if (jsonObj.has(ATTACHMENTS)) {
            val context = ArtifactUploadContext(artifactFileMap)
            buildTgzFile(artifactFileMap, jsonObj, context)
            buildPkgVersionFile(artifactFileMap, jsonObj, context)
            buildPkgFile(artifactInfo, artifactFileMap, jsonObj)
            // 上传构件
            ArtifactContextHolder.getRepository().upload(context)
            npmDependentHandler.updatePkgDepts(userId, artifactInfo, jsonObj, NpmOperationAction.PUBLISH)
            packageHandler.createVersion(userId, artifactInfo, jsonObj, context.getAttributes())
            NpmSuccessResponse.createEntitySuccess()
        } else {
            unPublishOperation(artifactInfo, jsonObj)
            NpmSuccessResponse.updatePkgSuccess()
        }
    }

    private fun unPublishOperation(artifactInfo: NpmArtifactInfo, jsonObj: JsonObject) {
        // 非publish操作 deprecate等操作
        val versions = jsonObj.getAsJsonObject(VERSIONS)
        versions.keySet().forEach {
            val name = jsonObj.get(NAME).asString
            val version = versions.getAsJsonObject(it)[VERSION].asString
            val metaData = buildMetaData(versions[it].asJsonObject)
            val tgzFullPath = String.format(NPM_PKG_TGZ_FULL_PATH, name, name, version)
            metadataClient.save(
                MetadataSaveRequest(
                    artifactInfo.projectId,
                    artifactInfo.repoName,
                    tgzFullPath,
                    metaData
                )
            )
        }
    }

    /**
     * 构造package.json文件
     */
    private fun buildPkgFile(artifactInfo: NpmArtifactInfo, artifactFileMap: ArtifactFileMap, jsonObj: JsonObject) {
        // 读取package.json文件去追加内容
        var pkgInfo = searchPackageInfo(artifactInfo) ?: JsonObject()
        val leastJsonObject = jsonObj.getAsJsonObject(VERSIONS)

        val distTags = getDistTags(jsonObj)!!
        if (pkgInfo.size() > 0 && pkgInfo.getAsJsonObject(VERSIONS).has(distTags.second)) {
            throw NpmArtifactExistException("cannot modify pre-existing version: ${distTags.second}.")
        }

        // first upload
        val gmtTime = TimeUtil.getGMTTime()
        val timeMap = if (pkgInfo.size() == 0) pkgInfo else pkgInfo.getAsJsonObject(TIME)!!
        if (pkgInfo.size() == 0) {
            jsonObj.addProperty(REV, REV_VALUE)
            pkgInfo = jsonObj
            timeMap.add(CREATED, GsonUtils.gson.toJsonTree(gmtTime))
        }

        pkgInfo.getAsJsonObject(VERSIONS).add(distTags.second, leastJsonObject.getAsJsonObject(distTags.second))
        pkgInfo.getAsJsonObject(DISTTAGS).addProperty(distTags.first, distTags.second)
        timeMap.add(distTags.second, GsonUtils.gson.toJsonTree(gmtTime))
        timeMap.add(MODIFIED, GsonUtils.gson.toJsonTree(gmtTime))
        pkgInfo.add(TIME, timeMap)
        val packageJsonFile = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(pkgInfo))
        artifactFileMap[NPM_PACKAGE_JSON_FILE] = packageJsonFile
    }

    private fun getDistTags(jsonObj: JsonObject): Pair<String, String>? {
        val distTags = jsonObj.getAsJsonObject(DISTTAGS)
        distTags.entrySet().forEach {
            return Pair(it.key, it.value.asString)
        }
        return null
    }

    /**
     * 构造pkgName-version.json文件
     */
    private fun buildPkgVersionFile(
        artifactFileMap: ArtifactFileMap,
        jsonObj: JsonObject,
        context: ArtifactUploadContext
    ) {
        val distTags = getDistTags(jsonObj)!!
        val name = jsonObj.get(NAME).asString
        val versionJsonObj = jsonObj.getAsJsonObject(VERSIONS).getAsJsonObject(distTags.second)
        val packageJsonWithVersionFile = ArtifactFileFactory.build(
            GsonUtils.gson.toJson(versionJsonObj).byteInputStream()
        )
        artifactFileMap[NPM_PACKAGE_VERSION_JSON_FILE] = packageJsonWithVersionFile
        // 添加相关属性
        context.putAttribute(ATTRIBUTE_OCTET_STREAM_SHA1, versionJsonObj.getAsJsonObject(DIST).get(SHASUM).asString)
        context.putAttribute(NPM_METADATA, buildMetaData(versionJsonObj))
        context.putAttribute(NPM_PKG_VERSION_JSON_FILE_FULL_PATH, String.format(NPM_PKG_VERSION_METADATA_FULL_PATH, name, name, distTags.second))
        context.putAttribute(NPM_PKG_JSON_FILE_FULL_PATH, String.format(NPM_PKG_METADATA_FULL_PATH, name))
    }

    private fun buildMetaData(versionJsonObj: JsonObject): Map<String, String> {
        val metaData = GsonUtils.gson.fromJson(versionJsonObj, NpmMetaData::class.java)
        return BeanUtils.beanToMap(metaData)
    }

    /**
     * 构造pkgName-version.tgz文件
     */
    private fun buildTgzFile(
        artifactFileMap: ArtifactFileMap,
        jsonObj: JsonObject,
        context: ArtifactUploadContext
    ) {
        val attachments = getAttachmentsInfo(jsonObj, context)
        val tgzFile = ArtifactFileFactory.build(Base64.decodeBase64(attachments.get(DATA)?.asString).inputStream())
        artifactFileMap[NPM_PACKAGE_TGZ_FILE] = tgzFile
    }

    /**
     * 获取文件模块相关信息
     */
    private fun getAttachmentsInfo(jsonObj: JsonObject, context: ArtifactUploadContext): JsonObject {
        val distTags = getDistTags(jsonObj)!!
        val name = jsonObj.get(NAME).asString
        logger.info("current pkgName : $name ,current version : ${distTags.second}")
        val attachKey = "$name$FILE_DASH${distTags.second}$FILE_SUFFIX"
        val mutableMap = jsonObj.getAsJsonObject(ATTACHMENTS).getAsJsonObject(attachKey)
        context.putAttribute(NPM_PKG_TGZ_FILE_FULL_PATH, String.format(NPM_PKG_TGZ_FULL_PATH, name, name, distTags.second))
        context.putAttribute(APPLICATION_OCTET_STEAM, mutableMap.get(CONTENT_TYPE).asString)
        context.putAttribute(LENGTH, mutableMap[LENGTH].asLong)
        jsonObj.add(ATTACHMENTS, JsonNull.INSTANCE)
        // jsonObj.remove(ATTACHMENTS)
        return mutableMap
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun searchPackageInfo(artifactInfo: NpmArtifactInfo): JsonObject? {
        if (StringUtils.equals(artifactInfo.version, LATEST)) {
            return searchLatestVersionMetadata(artifactInfo)
        }
        return searchVersionMetadata(artifactInfo)
    }

    private fun searchVersionMetadata(artifactInfo: NpmArtifactInfo): JsonObject? {
        val context = ArtifactQueryContext()
        context.putAttribute(NPM_FILE_FULL_PATH, getFileFullPath(artifactInfo))
        return ArtifactContextHolder.getRepository().query(context) as? JsonObject
    }

    private fun searchLatestVersionMetadata(artifactInfo: NpmArtifactInfo): JsonObject? {
        with(artifactInfo) {
            val scopePkg = if (StringUtils.isEmpty(scope)) pkgName else "$scope/$pkgName"
            val fullPath = String.format(NPM_PKG_METADATA_FULL_PATH, scopePkg)
            val context = ArtifactQueryContext()
            context.putAttribute(NPM_FILE_FULL_PATH, fullPath)
            //val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
            val npmMetaData = ArtifactContextHolder.getRepository().query(context) as? JsonObject
                ?: throw NpmArtifactNotFoundException("document not found!")
            val latestPackageVersion = npmMetaData.getAsJsonObject(DISTTAGS)[LATEST].asString
            val npmArtifactInfo = NpmArtifactInfo(
                projectId, repoName, artifactUri, scope, pkgName, latestPackageVersion
            )
            return searchVersionMetadata(npmArtifactInfo)
        }
    }

    private fun getFileFullPath(artifactInfo: NpmArtifactInfo): String {
        val scope = artifactInfo.scope
        val pkgName = artifactInfo.pkgName
        val version = artifactInfo.version
        val scopePkg = if (StringUtils.isEmpty(scope)) pkgName else "$scope/$pkgName"
        return if (StringUtils.isEmpty(version)) String.format(NPM_PKG_METADATA_FULL_PATH, scopePkg) else String.format(
            NPM_PKG_VERSION_METADATA_FULL_PATH,
            scopePkg,
            scopePkg,
            version
        )
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun download(artifactInfo: NpmArtifactInfo) {
        val context = ArtifactDownloadContext()
        val requestURI = HttpContextHolder.getRequest().requestURI
        context.putAttribute(NPM_FILE_FULL_PATH, requestURI.substringAfterLast(artifactInfo.repoName))
        ArtifactContextHolder.getRepository().download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun unpublish(userId: String, artifactInfo: NpmArtifactInfo): NpmDeleteResponse {
        val fullPathList = mutableListOf<String>()
        val pkgInfo = searchPackageInfo(artifactInfo)
            ?: throw NpmArtifactNotFoundException("document not found")
        val name = pkgInfo[NAME].asString
        fullPathList.add(".npm/$name")
        fullPathList.add(name)
        val context = ArtifactRemoveContext()
        context.putAttribute(NPM_FILE_FULL_PATH, fullPathList)
        ArtifactContextHolder.getRepository().remove(context)
        npmDependentHandler.updatePkgDepts(userId, artifactInfo, pkgInfo, NpmOperationAction.UNPUBLISH)
        packageHandler.deletePackage(userId, name, artifactInfo)
        return NpmDeleteResponse(true, name, REV_VALUE)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun updatePkg(artifactInfo: NpmArtifactInfo, body: String): NpmSuccessResponse {
        body.takeIf { StringUtils.isNotBlank(it) } ?: throw ArtifactNotFoundException("request body not found!")
        val jsonObj = JsonParser.parseString(body).asJsonObject
        val name = jsonObj.get(NAME).asString

        val artifactFileMap = ArtifactFileMap()
        val pkgFile = ArtifactFileFactory.build(GsonUtils.gson.toJson(jsonObj).byteInputStream())
        val context = ArtifactUploadContext(artifactFileMap)
        artifactFileMap[NPM_PACKAGE_JSON_FILE] = pkgFile
        context.putAttribute(NPM_PKG_JSON_FILE_FULL_PATH, String.format(NPM_PKG_METADATA_FULL_PATH, name))
        ArtifactContextHolder.getRepository().upload(context)
        logger.info("update package $name success!")
        return NpmSuccessResponse.updatePkgSuccess()
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun unPublishPkgWithVersion(userId: String, artifactInfo: NpmArtifactInfo, name: String, version: String): NpmDeleteResponse {
        val fullPathList = mutableListOf<String>()
        val tgzFileName = String.format("%s-%s.tgz", name, version)
        fullPathList.add(NpmUtils.getTgzPath(name, version))
        fullPathList.add(NpmUtils.getVersionPackageMetadataPath(name, version))
        val context = ArtifactRemoveContext()
        context.putAttribute(NPM_FILE_FULL_PATH, fullPathList)
        ArtifactContextHolder.getRepository().remove(context)
        logger.info("user: [$userId] delete package $tgzFileName success")
        // 删除包管理中对应的version
        packageHandler.deleteVersion(userId, name, version, artifactInfo)
        return NpmDeleteResponse(true, tgzFileName, REV_VALUE)
    }

    /**
     *
     * 搜索结果需要按照时间排序
     */
    @Suppress("UNCHECKED_CAST")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun search(artifactInfo: NpmArtifactInfo, searchRequest: MetadataSearchRequest): NpmSearchResponse {
        val context = ArtifactSearchContext()
        context.putAttribute(SEARCH_REQUEST, searchRequest)
        val npmSearchInfoMapList = ArtifactContextHolder.getRepository().search(context) as List<NpmSearchInfoMap>
        return NpmSearchResponse(npmSearchInfoMapList)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun getDistTagsInfo(artifactInfo: NpmArtifactInfo): Map<String, String> {
        val context = ArtifactQueryContext()
        context.putAttribute(NPM_FILE_FULL_PATH,
            String.format(NPM_PKG_METADATA_FULL_PATH, artifactInfo.artifactUri.trimStart('/').removeSuffix("/dist-tags")))
        //val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        val pkgInfo = ArtifactContextHolder.getRepository().query(context) as? JsonObject
        return pkgInfo?.let {
            GsonUtils.gsonToMaps<String>(it.get(DISTTAGS))
        } ?: ERROR_MAP
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun addDistTags(artifactInfo: NpmArtifactInfo, body: String): NpmSuccessResponse {
        val context = ArtifactQueryContext()
        val uriInfo = artifactInfo.artifactUri.split(DISTTAGS)
        val name = uriInfo[0].trimStart('/').trimEnd('/')
        val tag = uriInfo[1].trimStart('/')
        context.putAttribute(NPM_FILE_FULL_PATH, String.format(NPM_PKG_METADATA_FULL_PATH, name))
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        val pkgInfo = repository.query(context) as JsonObject
        pkgInfo.getAsJsonObject(DISTTAGS).addProperty(tag, body.replace("\"", ""))
        val artifactFile = ArtifactFileFactory.build(GsonUtils.gson.toJson(pkgInfo).byteInputStream())
        val uploadContext = ArtifactUploadContext(artifactFile)
        uploadContext.putAttribute(OCTET_STREAM + "_full_path", String.format(NPM_PKG_METADATA_FULL_PATH, name))
        repository.upload(uploadContext)
        return NpmSuccessResponse.createTagSuccess()
    }

    fun deleteDistTags(artifactInfo: NpmArtifactInfo) {
        val context = ArtifactQueryContext()
        val uriInfo = artifactInfo.artifactUri.split(DISTTAGS)
        val name = uriInfo[0].trimStart('/').trimEnd('/')
        val tag = uriInfo[1].trimStart('/')
        context.putAttribute(NPM_FILE_FULL_PATH, String.format(NPM_PKG_METADATA_FULL_PATH, name))
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        val pkgInfo = repository.query(context) as JsonObject
        pkgInfo.getAsJsonObject(DISTTAGS).remove(tag)
        val artifactFile = ArtifactFileFactory.build(GsonUtils.gson.toJson(pkgInfo).byteInputStream())
        val uploadContext = ArtifactUploadContext(artifactFile)
        uploadContext.putAttribute(OCTET_STREAM + "_full_path", String.format(NPM_PKG_METADATA_FULL_PATH, name))
        repository.upload(uploadContext)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmService::class.java)
    }
}
