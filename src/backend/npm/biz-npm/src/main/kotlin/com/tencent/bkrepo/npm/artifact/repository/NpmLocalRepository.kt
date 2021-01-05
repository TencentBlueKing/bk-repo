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

package com.tencent.bkrepo.npm.artifact.repository

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.migration.MigrateDetail
import com.tencent.bkrepo.common.artifact.repository.migration.PackageMigrateDetail
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.npm.async.NpmDependentHandler
import com.tencent.bkrepo.npm.constants.ATTRIBUTE_OCTET_STREAM_SHA1
<<<<<<< HEAD
=======
import com.tencent.bkrepo.npm.constants.AUTHOR
import com.tencent.bkrepo.npm.constants.DATE
import com.tencent.bkrepo.npm.constants.DESCRIPTION
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.DISTTAGS
import com.tencent.bkrepo.npm.constants.KEYWORDS
import com.tencent.bkrepo.npm.constants.LAST_MODIFIED_DATE
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.MAINTAINERS
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import com.tencent.bkrepo.npm.constants.METADATA
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_METADATA
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_TGZ_FILE
<<<<<<< HEAD
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.model.metadata.NpmPackageMetaData
import com.tencent.bkrepo.npm.model.metadata.NpmVersionMetadata
import com.tencent.bkrepo.npm.pojo.NpmSearchInfo
import com.tencent.bkrepo.npm.pojo.NpmSearchInfoMap
import com.tencent.bkrepo.npm.pojo.enums.NpmOperationAction
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import com.tencent.bkrepo.npm.properties.NpmProperties
=======
import com.tencent.bkrepo.npm.constants.NPM_PKG_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_FULL_PATH
import com.tencent.bkrepo.npm.constants.PACKAGE
import com.tencent.bkrepo.npm.constants.PKG_NAME
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.constants.SIZE
import com.tencent.bkrepo.npm.constants.TARBALL
import com.tencent.bkrepo.npm.constants.TIME
import com.tencent.bkrepo.npm.constants.VERSION
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
import com.tencent.bkrepo.npm.pojo.enums.NpmOperationAction
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import com.tencent.bkrepo.npm.pojo.migration.MigrationFailDataDetailInfo
import com.tencent.bkrepo.npm.pojo.migration.VersionFailDetail
import com.tencent.bkrepo.npm.utils.GsonUtils
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.npm.utils.OkHttpUtil
import com.tencent.bkrepo.npm.utils.TimeUtil
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import okhttp3.Response
<<<<<<< HEAD
=======
import okhttp3.ResponseBody
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import kotlin.system.measureTimeMillis

@Component
<<<<<<< HEAD
class NpmLocalRepository(
    private val npmProperties: NpmProperties,
    private val okHttpUtil: OkHttpUtil,
    private val npmDependentHandler: NpmDependentHandler
) : LocalRepository() {

    override fun onUploadValidate(context: ArtifactUploadContext) {
        // 不为空说明上传的是tgz文件
        context.getStringAttribute("attachments.content_type")?.let {
            it.takeIf { it == MediaType.APPLICATION_OCTET_STREAM_VALUE } ?: throw ArtifactValidateException(
                "Request MIME_TYPE is not ${MediaType.APPLICATION_OCTET_STREAM_VALUE}"
=======
class NpmLocalRepository : LocalRepository() {

    @Value("\${npm.migration.remote.registry: ''}")
    private val registry: String = StringPool.EMPTY

    @Value("\${npm.tarball.prefix: ''}")
    private val tarballPrefix: String = StringPool.SLASH

    @Autowired
    private lateinit var npmDependentHandler: NpmDependentHandler

    @Autowired
    private lateinit var okHttpUtil: OkHttpUtil

    override fun onUploadValidate(context: ArtifactUploadContext) {
        super.onUploadValidate(context)
        context.artifactFileMap.entries.forEach { (name, file) ->
            if (name == NPM_PACKAGE_TGZ_FILE) {
                // 校验MIME_TYPE
                context.contextAttributes[APPLICATION_OCTET_STEAM].takeIf {
                    it == MediaType.APPLICATION_OCTET_STREAM_VALUE
                } ?: throw ArtifactValidateException(
                    "Request MIME_TYPE is not ${MediaType.APPLICATION_OCTET_STREAM_VALUE}"
                )
                // 计算sha1并校验
                val calculatedSha1 = file.getInputStream().sha1()
                val uploadSha1 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA1] as String?
                if (uploadSha1 != null && calculatedSha1 != uploadSha1) {
                    throw ArtifactValidateException("File shasum validate failed.")
                }
            }
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        context.artifactFileMap.entries.forEach { (name, _) ->
            val nodeCreateRequest = getNodeCreateRequest(name, context)
            storageService.store(
                nodeCreateRequest.sha256!!,
                context.getArtifactFile(name)!!,
                context.storageCredentials
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
            )
            // 计算sha1并校验
            val calculatedSha1 = context.getArtifactFile().getInputStream().sha1()
            val uploadSha1 = context.getStringAttribute(ATTRIBUTE_OCTET_STREAM_SHA1)
            if (uploadSha1 != null && calculatedSha1 != uploadSha1) {
                throw ArtifactValidateException("File shasum validate failed.")
            }
        }
    }

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val name = context.getStringAttribute("name") ?: StringPool.EMPTY
        return NodeCreateRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            folder = false,
            fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!,
            size = context.getArtifactFile().getSize(),
            sha256 = context.getArtifactSha256(),
            md5 = context.getArtifactMd5(),
            operator = context.userId,
            metadata = parseMetaData(name, context.getAttributes()),
            overwrite = name != NPM_PACKAGE_TGZ_FILE
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseMetaData(name: String, contextAttributes: Map<String, Any>): Map<String, String> {
        return if (name == NPM_PACKAGE_TGZ_FILE) contextAttributes[NPM_METADATA] as Map<String, String> else emptyMap()
    }

    override fun query(context: ArtifactQueryContext): InputStream? {
        val fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)
        return this.onQuery(context) ?: run {
            logger.warn("Artifact [$fullPath] not found in repo [${context.projectId}/${context.repoName}]")
            null
        }
    }

    private fun onQuery(context: ArtifactQueryContext): InputStream? {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!
        val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
        if (node == null || node.folder) return null
        return storageService.load(node.sha256!!, Range.full(node.size), context.storageCredentials)
            .also {
                logger.info("search artifact [$fullPath] success!")
            }
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): DownloadStatisticsAddRequest? {
        with(context) {
            val packageInfo = NpmUtils.parseNameAndVersionFromFullPath(artifactInfo.getArtifactFullPath())
            with(packageInfo) {
                return DownloadStatisticsAddRequest(projectId, repoName, PackageKeys.ofNpm(first), first, second)
            }
        }
    }

<<<<<<< HEAD
    override fun search(context: ArtifactSearchContext): List<NpmSearchInfoMap> {
        val searchRequest = context.getAttribute<MetadataSearchRequest>(SEARCH_REQUEST)!!

        val queryModel = NodeQueryBuilder()
            .select("projectId", "repoName", "fullPath", "metadata", "lastModifiedDate")
            .sortByDesc("lastModifiedDate")
            .page(searchRequest.from, searchRequest.size)
            .projectId(context.projectId).repoName(context.repoName)
            .fullPath(".tgz", OperationType.SUFFIX)
            .or()
            .metadata("name", searchRequest.text, OperationType.MATCH)
            .metadata("description", searchRequest.text, OperationType.MATCH)
            .metadata("maintainers", searchRequest.text, OperationType.MATCH)
            .metadata("version", searchRequest.text, OperationType.MATCH)
            .metadata("keywords", searchRequest.text, OperationType.MATCH)
            .build()
        val data = nodeClient.search(queryModel).data ?: run {
            logger.warn("failed to find npm package in repo [${context.projectId}/${context.repoName}]")
            return emptyList()
        }
        return transferRecords(data.records)
    }

    @Suppress("UNCHECKED_CAST")
    private fun transferRecords(records: List<Map<String, Any?>>): List<NpmSearchInfoMap> {
        val mapListInfo = mutableListOf<NpmSearchInfoMap>()
        if (records.isNullOrEmpty()) return emptyList()
        records.forEach {
            val metadata = it[METADATA] as Map<String, Any>
            mapListInfo.add(
                NpmSearchInfoMap(
                    NpmSearchInfo(
                        metadata["name"] as? String,
                        metadata["description"] as? String,
                        metadata["maintainers"] as? List<Map<String, Any>> ?: emptyList(),
                        metadata["version"] as? String,
                        it["lastModifiedDate"] as String,
                        metadata["keywords"] as? List<String> ?: emptyList(),
                        metadata["author"] as? Map<String, Any> ?: emptyMap()
                    )
                )
            )
=======
    private fun onSearch(context: ArtifactSearchContext): JsonObject? {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as String
        val node = nodeClient.detail(projectId, repoName, fullPath).data
        if (node == null || node.folder) return null
        val inputStream =
            storageService.load(node.sha256!!, Range.full(node.size), context.storageCredentials)
                .also {
                    logger.info("search artifact [$fullPath] success!")
                }
        return inputStream?.let { getPkgInfo(it) }
    }

    private fun getPkgInfo(inputStream: ArtifactInputStream): JsonObject {
        val fileJson = GsonUtils.transferInputStreamToJson(inputStream)
        val name = fileJson.get(NAME).asString
        if (!fileJson.has(VERSIONS)) {
            // 根据配置和请求头来进行判断返回的URL
            val oldTarball = fileJson.getAsJsonObject(DIST)[TARBALL].asString
            fileJson.getAsJsonObject(DIST).addProperty(
                TARBALL,
                NpmUtils.buildPackageTgzTarball(oldTarball, tarballPrefix, name)
            )
        } else {
            val versions = fileJson.getAsJsonObject(VERSIONS)
            versions.keySet().forEach {
                val versionObject = versions.getAsJsonObject(it)
                val oldTarball = versionObject.getAsJsonObject(DIST)[TARBALL].asString
                versionObject.getAsJsonObject(DIST).addProperty(
                    TARBALL,
                    NpmUtils.buildPackageTgzTarball(oldTarball, tarballPrefix, name)
                )
            }
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        }
        return mapListInfo
    }

    override fun remove(context: ArtifactRemoveContext) {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getAttribute<List<*>>(NPM_FILE_FULL_PATH)
        val userId = context.userId
        fullPath?.forEach {
            nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, it.toString(), userId))
            logger.info("delete artifact $it success.")
        }
    }

    /**
     *  todo
     *  迁移成功之后需要创建包
     *  迁移tgz包时需要创建node元数据
     */
    override fun migrate(context: ArtifactMigrateContext): MigrateDetail {
        val dataSet = context.getAttribute<Set<String>>("migrationDateSet")!!
        val packageList: MutableList<PackageMigrateDetail> = mutableListOf()
        with(context) {
            val iterator = dataSet.iterator()
            val millis = measureTimeMillis {
                while (iterator.hasNext()) {
                    val packageMigrateDetail = doMigrate(context, iterator.next())
                    packageList.add(packageMigrateDetail)
                }
            }
            return MigrateDetail(
                projectId,
                repoName,
                packageList,
                Duration.ofMillis(millis),
                "npm package migrate result"
            )
        }
    }

    private fun doMigrate(context: ArtifactMigrateContext, packageName: String): PackageMigrateDetail {
        val packageMetaData = queryRemotePackageMetadata(packageName)
        return migratePackageArtifact(context, packageMetaData)
    }

    private fun queryRemotePackageMetadata(packageName: String): NpmPackageMetaData {
        val url = UrlFormatter.format(npmProperties.migration.remoteRegistry, packageName)
        var response: Response? = null
        try {
            response = okHttpUtil.doGet(url)
            return response.body()!!.byteStream()
                .use { JsonUtils.objectMapper.readValue(it, NpmPackageMetaData::class.java) }
        } catch (exception: IOException) {
            logger.error("migrate: http send [$url] for search [$packageName/package.json] file failed, {}", exception)
            throw exception
        } finally {
            response?.body()?.close()
        }
    }

<<<<<<< HEAD
    private fun migratePackageArtifact(
=======
    private fun addPackageVersion(
        cachePackageJson: JsonObject,
        remoteJson: JsonObject,
        failVersionSet: Set<String>,
        versionSizeMap: Map<String, Long>
    ): JsonObject {
        val differentVersionSet = mutableSetOf<String>()
        val pkgName = cachePackageJson[NAME].asString
        val remoteVersions = remoteJson.getAsJsonObject(VERSIONS)
        val localVersions = cachePackageJson.getAsJsonObject(VERSIONS)
        val remoteVersionsSet = remoteVersions.keySet()
        val cacheVersionsSet = localVersions.keySet()
        remoteVersionsSet.removeAll(failVersionSet)

        val remoteDistTags = remoteJson.getAsJsonObject(DISTTAGS)
        val localDistTags = cachePackageJson.getAsJsonObject(DISTTAGS)
        // 将拉取的包的dist_tags迁移过来
        remoteDistTags.keySet().forEach {
            if (!localDistTags.keySet().contains(it)) {
                localDistTags.addProperty(it, remoteDistTags[it].asString)
            }
        }

        val remoteLatest = remoteDistTags[LATEST].asString
        val localLatest = localDistTags[LATEST].asString
        val remoteLatestTime = remoteJson.getAsJsonObject(TIME)[remoteLatest].asString
        val localLatestTime = cachePackageJson.getAsJsonObject(TIME)[localLatest].asString
        if (TimeUtil.compareTime(remoteLatestTime, localLatestTime)) {
            cachePackageJson.getAsJsonObject(DISTTAGS).addProperty(LATEST, remoteLatest)
            cachePackageJson.getAsJsonObject(TIME)
                .addProperty(MODIFIED, remoteJson.getAsJsonObject(TIME)[MODIFIED].asString)
        }
        if (remoteVersionsSet.size > 0) {
            // 说明有版本更新,将新增的版本迁移过来
            remoteVersionsSet.forEach { version ->
                val localDistObject = localVersions.getAsJsonObject(version).getAsJsonObject(DIST)
                val versionJson = remoteVersions.getAsJsonObject(version)
                val versionTime = remoteJson.getAsJsonObject(TIME)[version].asString
                // 以自身为准，如果已经存在该版本则比较时间，将最新的迁移过来
                if (!cacheVersionsSet.contains(version)) {
                    localVersions.add(version, versionJson)
                    cachePackageJson.getAsJsonObject(TIME).addProperty(version, versionTime)
                    if(!localDistObject.has(SIZE)){
                        localDistObject.addProperty(SIZE, versionSizeMap[version])
                    }
                    remoteDistTags.entrySet().forEach {
                        if (version == it.value.asString) {
                            localDistTags.addProperty(it.key, version)
                        }
                    }
                    differentVersionSet.add(version)
                } else {
                    val localVersionTime = cachePackageJson.getAsJsonObject(TIME)[version].asString
                    if (TimeUtil.compareTime(versionTime, localVersionTime)) {
                        localVersions.add(version, versionJson)
                        cachePackageJson.getAsJsonObject(TIME).addProperty(version, versionTime)
                        if(!localDistObject.has(SIZE)){
                            localDistObject.addProperty(SIZE, versionSizeMap[version])
                        }
                        remoteDistTags.entrySet().forEach {
                            if (version == it.value.asString) {
                                localDistTags.addProperty(it.key, version)
                            }
                        }
                        differentVersionSet.add(version)
                    }
                    // 兼容历史数据的dist-tags迁移问题
                    if (TimeUtil.isTimeNotBefore(versionTime, localVersionTime)) {
                        if(!localDistObject.has(SIZE)){
                            localDistObject.addProperty(SIZE, versionSizeMap[version])
                        }
                        remoteDistTags.entrySet().forEach {
                            if (version == it.value.asString && localDistTags.keySet().contains(it.key)) {
                                localDistTags.addProperty(it.key, version)
                            }
                        }
                    }
                }
            }
            logger.info("compare package.json and migrate different versions of the package [$pkgName] file is $differentVersionSet, size : ${differentVersionSet.size}")
        }
        return cachePackageJson
    }

    private fun comparePackageJson(
        remoteJson: JsonObject,
        failVersionSet: Set<String>,
        versionSizeMap: Map<String, Long>
    ): JsonObject? {
        if (failVersionSet.isEmpty()) return remoteJson
        val pkgName = remoteJson[NAME].asString
        val remoteVersions = remoteJson.getAsJsonObject(VERSIONS)
        val remoteVersionsSet = remoteVersions.keySet()
        remoteVersionsSet.removeAll(failVersionSet)

        //给dist添加size
        remoteVersionsSet.forEach {
            val distObject = remoteVersions.getAsJsonObject(it).getAsJsonObject(DIST)
            if (!distObject.has(SIZE)){
                distObject.addProperty(SIZE, versionSizeMap[it])
            }
        }

        val remoteDistTags = remoteJson.getAsJsonObject(DISTTAGS)
        val remoteTimeJsons = remoteJson.getAsJsonObject(TIME)
        val remoteLatest = remoteDistTags[LATEST].asString
        // 将拉取的包的dist_tags迁移过来
        val it = remoteDistTags.entrySet().iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (failVersionSet.contains(next.value.asString)) {
                it.remove()
                remoteTimeJsons.remove(next.value.asString)
            }
        }

        if (!remoteTimeJsons.has(remoteLatest)) {
            remoteTimeJsons.remove(MODIFIED)
            val dateList = remoteTimeJsons.entrySet().stream()
                .map { LocalDateTime.parse(it.value.asString, DateTimeFormatter.ISO_DATE_TIME) }
                .collect(Collectors.toList())
            val maxTime = Collections.max(dateList)
            val latestTime = maxTime.format(DateTimeFormatter.ofPattern(TimeUtil.FORMAT))
            remoteTimeJsons.entrySet().forEach {
                if (it.value.asString == latestTime) {
                    remoteDistTags.addProperty(LATEST, it.key)
                }
            }
            remoteTimeJsons.addProperty(MODIFIED, latestTime)
        }
        return if (remoteVersionsSet.isNotEmpty()) {
            remoteJson
        } else {
            logger.warn("package [$pkgName] with all versions migration failed!")
            null
        }
    }

    private fun installVersionAndTgzArtifact(
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        context: ArtifactMigrateContext,
        packageMetaData: NpmPackageMetaData
    ): PackageMigrateDetail {
        val name = packageMetaData.name!!
        val packageMigrateDetail = PackageMigrateDetail(name)

        var count = 0
<<<<<<< HEAD
        val totalSize = packageMetaData.versions.map.size
=======
        val totalSize = versions.keySet().size
        var versionSizeMap: Map<String, Long> = mutableMapOf()
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4

        val iterator = packageMetaData.versions.map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val version = entry.key
            val versionMetadata = entry.value
            try {
                measureTimeMillis {
<<<<<<< HEAD
                    val tarball = versionMetadata.dist?.tarball!!
                    storeVersionMetadata(context, versionMetadata)
                    storeTgzArtifact(context, tarball, name, version)
=======
                    val versionJson = versions.getAsJsonObject(version)
                    val tarball = versionJson.getAsJsonObject(DIST).get(TARBALL).asString
                    versionSizeMap = storeTgzArtifact(context, tarball, name, version)
                    storeVersionArtifact(context, versionJson, versionSizeMap)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
                }.apply {
                    logger.info(
                        "migrate npm package [$name] for version [$version] success, elapse $this ms. " +
                            "process rate: [${++count}/$totalSize]"
                    )
                }
                packageMigrateDetail.addSuccessVersion(version)
            } catch (ignored: Exception) {
                logger.error("migrate package [$name] for version [$version] failed， message： $ignored")
                // delete version metadata
                deleteVersionMetadata(context, name, version)
                packageMigrateDetail.addFailureVersion(version, ignored.toString())
            }
        }
<<<<<<< HEAD
        val failVersionList = packageMigrateDetail.failureVersionDetailList.map { it.version }
        migratePackageMetadata(context, packageMetaData, failVersionList)
        return packageMigrateDetail
=======
        val failVersionSet =
            migrationFailDataDetailInfo.versionSet.stream().map { it.version }.collect(Collectors.toSet())
        putPackageJsonFile(context, name, failVersionSet, remotePackageJson, versionSizeMap)
        return migrationFailDataDetailInfo
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    }

    /**
     * 迁移package.json文件
     */
    private fun migratePackageMetadata(
        context: ArtifactMigrateContext,
<<<<<<< HEAD
        packageMetaData: NpmPackageMetaData,
        failVersionList: List<String>
=======
        name: String,
        failVersionSet: Set<String>,
        remotePackageJson: JsonObject,
        versionSizeMap: Map<String, Long>
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    ) {
        val name = packageMetaData.name!!
        val fullPath = NpmUtils.getPackageMetadataPath(name)
        try {
            with(context) {
                val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
                val originalPackageMetadata = node?.let {
                    val inputStream = storageService.load(it.sha256!!, Range.full(it.size), storageCredentials)
                    JsonUtils.objectMapper.readValue(inputStream, NpmPackageMetaData::class.java)
                }
                val newPackageMetaData = if (originalPackageMetadata != null) {
                    // 比对合并package.json，将失败的版本去除
                    comparePackageVersion(originalPackageMetadata, packageMetaData, failVersionList)
                } else {
                    // 本地没有该文件直接迁移，将失败的版本去除
                    migratePackageVersion(packageMetaData, failVersionList)
                } ?: return
                // 调整tarball地址
                val versionMetaData = newPackageMetaData.versions.map.values.iterator().next()
                with(versionMetaData) {
                    if (!NpmUtils.isDashSeparateInTarball(name, version!!, dist?.tarball!!)) {
                        packageMetaData.versions.map.values.forEach {
                            adjustTarball(it)
                        }
                    }
                }
                // 存储package.json文件
                context.putAttribute(NPM_FILE_FULL_PATH, NpmUtils.getPackageMetadataPath(name))
                val artifactFile = JsonUtils.objectMapper.writeValueAsBytes(newPackageMetaData).inputStream()
                    .use { ArtifactFileFactory.build(it) }
                val nodeCreateRequest = buildMigrationNodeCreateRequest(context, artifactFile)
                storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
                // 添加依赖
                npmDependentHandler.updatePackageDependents(
                    context.userId, context.artifactInfo, newPackageMetaData, NpmOperationAction.MIGRATION
                )
                artifactFile.delete()
            }
        } catch (ignored: Exception) {
            logger.error("migrate package metadata for package [$name] failed. message: ${ignored.message}")
        }
<<<<<<< HEAD
=======
        val newPackageJson = (if (cacheArtifact != null) {
            val cachePackageJson = GsonUtils.transferInputStreamToJson(cacheArtifact!!)
            // 对比合并package.json，去除掉迁移失败的版本
            addPackageVersion(cachePackageJson, remotePackageJson, failVersionSet, versionSizeMap)
        } else {
            comparePackageJson(remotePackageJson, failVersionSet, versionSizeMap)
        }) ?: return
        // store package json file
        val newArtifactFile = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(newPackageJson))
        putArtifact(context, newArtifactFile)
        // 添加依赖
        npmDependentHandler.updatePkgDepts(
            context.userId, context.artifactInfo, newPackageJson, NpmOperationAction.MIGRATION
        )
        newArtifactFile.delete()
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    }

    private fun comparePackageVersion(
        originalPackageMetadata: NpmPackageMetaData,
        packageMetaData: NpmPackageMetaData,
        failVersionList: List<String>
    ): NpmPackageMetaData {
        val name = originalPackageMetadata.name
        val originalVersionSet = originalPackageMetadata.versions.map.keys
        val remoteVersionList = packageMetaData.versions.map.keys
        remoteVersionList.removeAll(originalVersionSet)
        remoteVersionList.removeAll(failVersionList)

        val originalDistTags = originalPackageMetadata.distTags
        val originalTimeMap = originalPackageMetadata.time
        val distTags = packageMetaData.distTags
        val timeMap = packageMetaData.time
        // 迁移dist_tags
        distTags.getMap().entries.forEach {
            if (!originalDistTags.getMap().keys.contains(it.key)) {
                originalDistTags.set(it.key, it.value)
            }
        }
<<<<<<< HEAD
        // 迁移latest版本
        val remoteLatest = NpmUtils.getLatestVersionFormDistTags(distTags)
        val originalLatest = NpmUtils.getLatestVersionFormDistTags(originalDistTags)
        if (TimeUtil.compareTime(timeMap.get(remoteLatest), originalTimeMap.get(originalLatest))) {
            originalDistTags.set("latest", remoteLatest)
            originalTimeMap.add("modified", timeMap.get("modified"))
        }
        logger.info("the different versions of the  package [$name] is [$remoteVersionList], size : ${remoteVersionList.size}")
        if (remoteVersionList.isNotEmpty()) {
            // 说明有版本更新，将新增的版本迁移过来
            remoteVersionList.forEach { it ->
                val versionMetadata = packageMetaData.versions.map[it]!!
                val versionTime = timeMap.get(it)
                // 比较两边都存在相同的版本，则比较上传时间, 如果remote版本在后面上传，则进行迁移
                if (originalVersionSet.contains(it)) {
                    if (TimeUtil.compareTime(timeMap.get(it), originalTimeMap.get(it))) {
                        originalPackageMetadata.versions.map[it] = versionMetadata
                        originalTimeMap.add(it, versionTime)
                        distTags.getMap().entries.forEach {
                            originalDistTags.set(it.key, it.value)
                        }
                    }
                } else {
                    originalPackageMetadata.versions.map[it] = versionMetadata
                    originalTimeMap.add(it, versionTime)
                }
            }
=======
    }

    private fun storeVersionArtifact(
        context: ArtifactMigrateContext,
        versionJson: JsonObject,
        versionSizeMap: Map<String, Long>
    ) {
        val name = versionJson[NAME].asString
        val version = versionJson[VERSION].asString
        if (!versionJson.getAsJsonObject(DIST).has(SIZE)) {
            versionJson.getAsJsonObject(DIST).addProperty(SIZE, versionSizeMap[version])
        }
        val versionArtifactFile = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(versionJson))
        val fullPath = String.format(NPM_PKG_VERSION_FULL_PATH, name, name, version)
        context.contextAttributes[NPM_FILE_FULL_PATH] = fullPath
        if (nodeClient.exist(context.artifactInfo.projectId, context.artifactInfo.repoName, fullPath).data!!) {
            logger.info(
                "package [$name] with version json file [$name-$version.json] " +
                    "is already exists in repository, skip migration."
            )
            return
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        }
        return originalPackageMetadata
    }

    /**
     * 迁移版本成功的包
     */
<<<<<<< HEAD
    private fun migratePackageVersion(
        packageMetaData: NpmPackageMetaData,
        failVersionList: List<String>
    ): NpmPackageMetaData? {
        if (failVersionList.isEmpty()) return packageMetaData
        val remoteVersionList = packageMetaData.versions.map.keys
        remoteVersionList.removeAll(failVersionList)
        if (remoteVersionList.isEmpty()) return null

        val distTags = packageMetaData.distTags
        val timeMap = packageMetaData.time.getMap() as MutableMap
        val latest = NpmUtils.getLatestVersionFormDistTags(distTags)

        val iterator = distTags.getMap().entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (failVersionList.contains(entry.value)) {
                iterator.remove()
                timeMap.remove(entry.value)
            }
        }
        if (!timeMap.containsKey(latest)) {
            timeMap.remove("modified")
            val timeList = timeMap.entries.map { LocalDateTime.parse(it.value, DateTimeFormatter.ISO_DATE_TIME) }
            val maxTime = Collections.max(timeList)
            val latestTime = maxTime.format(DateTimeFormatter.ofPattern(TimeUtil.FORMAT))
            timeMap.entries.forEach {
                if (it.value == latestTime) {
                    distTags.set("latest", it.key)
=======
    private fun storeTgzArtifact(
        context: ArtifactMigrateContext,
        tarball: String,
        name: String,
        version: String
    ): Map<String, Long> {
        // 包的大小信息
        val tgzSizeInfoMap = mutableMapOf<String, Long>()
        var response: Response? = null
        val tgzFilePath = tarball.substring(tarball.indexOf(name))
        context.contextAttributes[NPM_FILE_FULL_PATH] = "/$tgzFilePath"
        // hit cache continue
        getCacheArtifact(context)?.let {
            logger.info(
                "package [$name] with tgz file [$tgzFilePath] is already exists in repository, skip migration."
            )
            return mutableMapOf()
        }
        try {
            measureTimeMillis {
                response = okHttpUtil.doGet(tarball)
                if (checkResponse(response!!)) {
                    val artifactFile = createTempFile(response?.body()!!)
                    putArtifact(context, artifactFile)
                    tgzSizeInfoMap[version] = artifactFile.getSize()
                    artifactFile.delete()
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
                }
            }
            timeMap["modified"] = latestTime
        }
<<<<<<< HEAD
        return packageMetaData
=======
        return tgzSizeInfoMap
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    }

    private fun adjustTarball(versionMetaData: NpmVersionMetadata) {
        with(versionMetaData) {
            versionMetaData.dist!!.tarball = NpmUtils.formatTarballWithDash(name!!, version!!, dist?.tarball!!)
        }
    }

    private fun storeVersionMetadata(context: ArtifactMigrateContext, versionMetadata: NpmVersionMetadata) {
        with(context) {
            val name = versionMetadata.name!!
            val version = versionMetadata.version!!
            if (!NpmUtils.isDashSeparateInTarball(name, version, versionMetadata.dist?.tarball!!)) {
                adjustTarball(versionMetadata)
            }
            val inputStream = JsonUtils.objectMapper.writeValueAsString(versionMetadata).byteInputStream()
            val artifactFile = inputStream.use { ArtifactFileFactory.build(it) }
            val fullPath = NpmUtils.getVersionPackageMetadataPath(name, version)
            context.putAttribute(NPM_FILE_FULL_PATH, fullPath)
            if (nodeClient.checkExist(projectId, repoName, fullPath).data!!) {
                logger.info(
                    "package [$name] with version metadata [$name-$version.json] " +
                        "is already exists in repository [$projectId/$repoName], skip migration."
                )
                return
            }
            val nodeCreateRequest = buildMigrationNodeCreateRequest(context, artifactFile)
            storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
            logger.info("migrate npm package [$name] with version metadata [$name-$version.json] success.")
            artifactFile.delete()
        }
    }

    private fun storeTgzArtifact(context: ArtifactMigrateContext, tarball: String, name: String, version: String) {
        with(context) {
            var response: Response? = null
            val fullPath = NpmUtils.getTgzPath(name, version)
            context.putAttribute(NPM_FILE_FULL_PATH, fullPath)
            // hit cache continue
            if (nodeClient.checkExist(projectId, repoName, fullPath).data!!) {
                logger.info(
                    "package [$name] with tgz file [$fullPath] is " +
                        "already exists in repository [$projectId/$repoName], skip migration."
                )
                return
            }
            try {
                measureTimeMillis {
                    response = okHttpUtil.doGet(tarball)
                    if (checkResponse(response!!)) {
                        // val artifactFile = response?.body()!!.byteStream().use { ArtifactFileFactory.build(it) }
                        val artifactFile = ArtifactFileFactory.build(response?.body()!!.byteStream())
                        val nodeCreateRequest = buildMigrationNodeCreateRequest(context, artifactFile)
                        storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
                        artifactFile.delete()
                    }
                }.apply {
                    logger.info(
                        "migrate npm package [$name] with tgz file [$fullPath] success, elapse $this ms."
                    )
                }
            } catch (exception: IOException) {
                logger.error("http send url [$tarball] for artifact [$fullPath] failed : $exception")
                throw exception
            } finally {
                response?.body()?.close()
            }
        }
    }

    private fun buildMigrationNodeCreateRequest(
        context: ArtifactMigrateContext,
        file: ArtifactFile
    ): NodeCreateRequest {
        val repositoryDetail = context.repositoryDetail
        val sha256 = file.getFileSha256()
        val md5 = file.getFileMd5()
        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!,
            size = file.getSize(),
            sha256 = sha256,
            md5 = md5,
            overwrite = true,
            operator = context.userId
        )
    }

    private fun deleteVersionMetadata(context: ArtifactMigrateContext, name: String, version: String) {
        val fullPath = NpmUtils.getVersionPackageMetadataPath(name, version)
        with(context) {
            if (nodeClient.checkExist(projectId, repoName, fullPath).data!!) {
                val nodeDeleteRequest = NodeDeleteRequest(projectId, repoName, fullPath, userId)
                nodeClient.deleteNode(nodeDeleteRequest)
                logger.info(
                    "migrate package [$name] with version [$version] failed, " +
                        "delete package version metadata [$fullPath] success."
                )
            }
        }
    }

    /**
     * 检查下载响应
     */
    private fun checkResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Download file from remote failed: [${response.code()}]")
            return false
        }
        return true
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmLocalRepository::class.java)
    }
}
