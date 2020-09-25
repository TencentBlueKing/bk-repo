package com.tencent.bkrepo.npm.artifact.repository

import com.google.gson.JsonObject
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.migration.MigrateDetail
import com.tencent.bkrepo.common.artifact.repository.migration.PackageMigrateDetail
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.npm.async.NpmDependentHandler
import com.tencent.bkrepo.npm.constants.APPLICATION_OCTET_STEAM
import com.tencent.bkrepo.npm.constants.ATTRIBUTE_OCTET_STREAM_SHA1
import com.tencent.bkrepo.npm.constants.AUTHOR
import com.tencent.bkrepo.npm.constants.DESCRIPTION
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.DISTTAGS
import com.tencent.bkrepo.npm.constants.ID
import com.tencent.bkrepo.npm.constants.KEYWORDS
import com.tencent.bkrepo.npm.constants.LAST_MODIFIED_DATE
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.MAINTAINERS
import com.tencent.bkrepo.npm.constants.METADATA
import com.tencent.bkrepo.npm.constants.MODIFIED
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_METADATA
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_TGZ_FILE
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.PKG_NAME
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.constants.TARBALL
import com.tencent.bkrepo.npm.constants.TIME
import com.tencent.bkrepo.npm.constants.VERSION
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.pojo.NpmSearchInfo
import com.tencent.bkrepo.npm.pojo.NpmSearchInfoMap
import com.tencent.bkrepo.npm.pojo.enums.NpmOperationAction
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.npm.utils.OkHttpUtil
import com.tencent.bkrepo.npm.utils.TimeUtil
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import okhttp3.Response
import okhttp3.ResponseBody
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.stream.Collectors
import kotlin.system.measureTimeMillis

@Component
class NpmLocalRepository : LocalRepository() {

    @Autowired
    lateinit var metadataClient: MetadataClient

    @Value("\${npm.migration.remote.registry: ${StringPool.EMPTY}}")
    private val registry: String = StringPool.EMPTY

    @Value("\${npm.tarball.prefix: ${StringPool.SLASH}}")
    private val tarballPrefix: String = StringPool.SLASH

    @Autowired
    private lateinit var npmDependentHandler: NpmDependentHandler

    @Autowired
    private lateinit var okHttpUtil: OkHttpUtil

    override fun onUploadValidate(context: ArtifactUploadContext) {
        context.getArtifactFileMap().entries.forEach { (name, file) ->
            if (name == NPM_PACKAGE_TGZ_FILE) {
                // 校验MIME_TYPE
                context.getStringAttribute(APPLICATION_OCTET_STEAM).takeIf {
                    it == MediaType.APPLICATION_OCTET_STREAM_VALUE
                } ?: throw ArtifactValidateException(
                    "Request MIME_TYPE is not ${MediaType.APPLICATION_OCTET_STREAM_VALUE}"
                )
                // 计算sha1并校验
                val calculatedSha1 = file.getInputStream().sha1()
                val uploadSha1 = context.getStringAttribute(ATTRIBUTE_OCTET_STREAM_SHA1)
                if (uploadSha1 != null && calculatedSha1 != uploadSha1) {
                    throw ArtifactValidateException("File shasum validate failed.")
                }
            }
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        context.getArtifactFileMap().entries.forEach { (name, _) ->
            val nodeCreateRequest = buildNodeCreateRequest(name, context)
            storageManager.storeArtifactFile(nodeCreateRequest,context.getArtifactFile(name),context.storageCredentials)
        }
    }

    private fun buildNodeCreateRequest(name: String, context: ArtifactUploadContext): NodeCreateRequest {
        val repositoryDetail = context.repositoryDetail
        val artifactFile = context.getArtifactFile(name)
        val sha256 = context.getArtifactSha256(name)
        val md5 = context.getArtifactMd5(name)

        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            fullPath = context.getStringAttribute(name + "_full_path")!!,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = context.userId,
            metadata = parseMetaData(name, context.getAttributes()),
            overwrite = name != NPM_PACKAGE_TGZ_FILE
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseMetaData(name: String, contextAttributes: Map<String, Any>): Map<String, String> {
        return if (name == NPM_PACKAGE_TGZ_FILE) contextAttributes[NPM_METADATA] as Map<String, String> else emptyMap()
    }

    override fun query(context: ArtifactQueryContext): JsonObject? {
        val fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)
        val searchResponse = this.onQuery(context)
        if (searchResponse == null) {
            logger.warn("Artifact[$fullPath] does not exist")
        }
        return searchResponse
    }

    private fun onQuery(context: ArtifactQueryContext): JsonObject? {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!
        val node = nodeClient.detail(projectId, repoName, fullPath).data
        if (node == null || node.folder) return null
        val inputStream =
            storageService.load(node.sha256!!, Range.full(node.size), context.storageCredentials)
                .also {
                    logger.info("search artifact [$fullPath] success!")
                }
        return inputStream?.let { getPkgInfo(context, it) }
    }

    private fun getPkgInfo(context: ArtifactQueryContext, inputStream: ArtifactInputStream): JsonObject {
        val fileJson = GsonUtils.transferInputStreamToJson(inputStream)
        val name = fileJson.get(NAME).asString
        val containsVersion = fileJson[ID].asString.substring(1).contains('@')
        if (containsVersion) {
            val version = fileJson[VERSION].asString
            val tgzFullPath = String.format(NPM_PKG_TGZ_FULL_PATH, name, name, version)
            val metadataInfo =
                metadataClient.query(context.artifactInfo.projectId, context.artifactInfo.repoName, tgzFullPath).data
            metadataInfo?.forEach { (key, value) ->
                if (StringUtils.isNotBlank(value)) fileJson.addProperty(key, value)
                if (key == KEYWORDS || key == MAINTAINERS) fileJson.add(key, GsonUtils.stringToArray(value))
            }
            // 根据配置和请求头来进行判断返回的URL
            val oldTarball = fileJson.getAsJsonObject(DIST)[TARBALL].asString
            fileJson.getAsJsonObject(DIST).addProperty(
                TARBALL,NpmUtils.buildPackageTgzTarball(oldTarball, tarballPrefix, name, context.artifactInfo)
            )
        } else {
            val versions = fileJson.getAsJsonObject(VERSIONS)
            versions.keySet().forEach {
                val tgzFullPath = String.format(NPM_PKG_TGZ_FULL_PATH, name, name, it)
                val metadataInfo =
                    metadataClient.query(context.artifactInfo.projectId, context.artifactInfo.repoName, tgzFullPath)
                        .data
                metadataInfo?.forEach { (key, value) ->
                    if (StringUtils.isNotBlank(value)) versions.getAsJsonObject(it).addProperty(key, value)
                    if (key == KEYWORDS || key == MAINTAINERS) versions.getAsJsonObject(it).add(
                        key,
                        GsonUtils.stringToArray(value)
                    )
                }
                val versionObject = versions.getAsJsonObject(it)
                val oldTarball = versionObject.getAsJsonObject(DIST)[TARBALL].asString
                versionObject.getAsJsonObject(DIST).addProperty(
                    TARBALL, NpmUtils.buildPackageTgzTarball(oldTarball, tarballPrefix, name, context.artifactInfo)
                )
            }
        }
        return fileJson
    }

    override fun remove(context: ArtifactRemoveContext) {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getAttribute<List<*>>(NPM_FILE_FULL_PATH)
        val userId = context.userId
        fullPath?.forEach {
            nodeClient.delete(NodeDeleteRequest(projectId, repoName, it as String, userId))
        }
    }

    override fun search(context: ArtifactSearchContext): List<NpmSearchInfoMap> {
        val searchRequest = context.getAttribute<MetadataSearchRequest>(SEARCH_REQUEST)!!
        val projectId = Rule.QueryRule("projectId", context.projectId)
        val repoName = Rule.QueryRule("repoName", context.repoName)
        val fullPath = Rule.QueryRule("fullPath", ".tgz", OperationType.SUFFIX)

        val nameMd = Rule.QueryRule("metadata.name", searchRequest.text, OperationType.MATCH)
        val descMd = Rule.QueryRule("metadata.description", searchRequest.text, OperationType.MATCH)
        val maintainerMd = Rule.QueryRule("metadata.maintainers", searchRequest.text, OperationType.MATCH)
        val versionMd = Rule.QueryRule("metadata.version", searchRequest.text, OperationType.MATCH)
        val keywordsMd = Rule.QueryRule("metadata.keywords", searchRequest.text, OperationType.MATCH)
        val metadata = Rule.NestedRule(
            mutableListOf(nameMd, descMd, maintainerMd, versionMd, keywordsMd),
            Rule.NestedRule.RelationType.OR
        )
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, fullPath, metadata))
        val queryModel = QueryModel(
            page = PageLimit(searchRequest.from, searchRequest.size),
            sort = Sort(listOf("lastModifiedDate"), Sort.Direction.DESC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata", "lastModifiedDate"),
            rule = rule
        )
        val result = nodeClient.query(queryModel)
        val data = result.data ?: return emptyList()
        return transferRecords(data.records)
    }

    @Suppress("UNCHECKED_CAST")
    private fun transferRecords(records: List<Map<String, Any?>>): List<NpmSearchInfoMap> {
        val mapListInfo = mutableListOf<NpmSearchInfoMap>()
        if (records.isNullOrEmpty()) return emptyList()
        records.forEach {
            val metadata = it[METADATA] as Map<*, *>
            mapListInfo.add(
                NpmSearchInfoMap(
                    NpmSearchInfo(
                        metadata[NAME] as? String,
                        metadata[DESCRIPTION] as? String,
                        GsonUtils.parseJsonArrayToList<Map<String,Any>>(metadata[MAINTAINERS] as? String),
                        metadata[VERSION] as? String,
                        it[LAST_MODIFIED_DATE] as String,
                        GsonUtils.parseJsonArrayToList<String>(metadata[KEYWORDS] as? String),
                        metadata[AUTHOR] as? Map<String, Any>
                    )
                )
            )
        }
        return mapListInfo
    }

    /**
     * 迁移功能（还未修改）
     */
    override fun migrate(context: ArtifactMigrateContext): MigrateDetail {
        val pkgJsonFile = searchPackageJsonFile(context)
        return installVersionAndTgzArtifact(context, pkgJsonFile)
    }

    private fun searchPackageJsonFile(context: ArtifactMigrateContext): JsonObject {
        val pkgName = context.getStringAttribute(PKG_NAME)
        val url = registry.trimEnd('/') + '/' + pkgName
        var response: Response? = null
        try {
            response = okHttpUtil.doGet(url)
            val artifactFile = createTempFile(response.body()!!)
            return GsonUtils.transferInputStreamToJson(artifactFile.getInputStream())
        } catch (exception: IOException) {
            logger.error("http send [$url] for search [$pkgName.json] file failed, {}", exception)
            throw exception
        } finally {
            response?.body()?.close()
        }
    }

    private fun addPackageVersion(
        cachePackageJson: JsonObject,
        remoteJson: JsonObject,
        failVersionSet: Set<String>
    ): JsonObject {
        val pkgName = cachePackageJson[NAME].asString
        val remoteVersions = remoteJson.getAsJsonObject(VERSIONS)
        val localVersions = cachePackageJson.getAsJsonObject(VERSIONS)
        val remoteVersionsSet = remoteVersions.keySet()
        val cacheVersionsSet = localVersions.keySet()
        remoteVersionsSet.removeAll(cacheVersionsSet)
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
        logger.info("the different versions of the  package [$pkgName] is $remoteVersionsSet, size : ${remoteVersionsSet.size}")
        if (remoteVersionsSet.size > 0) {
            // 说明有版本更新,将新增的版本迁移过来
            remoteVersionsSet.forEach {
                val versionJson = remoteVersions.getAsJsonObject(it)
                val versionTime = remoteJson.getAsJsonObject(TIME)[it].asString
                // 以自身为准，如果已经存在该版本则不迁移过来
                if (!cacheVersionsSet.contains(it)) {
                    localVersions.add(it, versionJson)
                    cachePackageJson.getAsJsonObject(TIME).addProperty(it, versionTime)
                }
            }
        }
        return cachePackageJson
    }

    private fun comparePackageJson(remoteJson: JsonObject, failVersionSet: Set<String>): JsonObject? {
        if (failVersionSet.isEmpty()) return remoteJson
        val pkgName = remoteJson[NAME].asString
        val remoteVersions = remoteJson.getAsJsonObject(VERSIONS)
        val remoteVersionsSet = remoteVersions.keySet()
        remoteVersionsSet.removeAll(failVersionSet)

        val remoteDistTags = remoteJson.getAsJsonObject(DISTTAGS)
        val remoteTimeJsons = remoteJson.getAsJsonObject(TIME)
        val remoteLatest = remoteDistTags[LATEST].asString
        // 将拉取的包的dist_tags迁移过来
        val it = remoteDistTags.entrySet().iterator()
            while (it.hasNext()){
                val next = it.next()
                if (failVersionSet.contains(next.value.asString)){
                    it.remove()
                    remoteTimeJsons.remove(next.value.asString)
            }
        }

        if (!remoteTimeJsons.has(remoteLatest)){
            remoteTimeJsons.remove(MODIFIED)
            val dateList = remoteTimeJsons.entrySet().stream().map { LocalDateTime.parse(it.value.asString, DateTimeFormatter.ISO_DATE_TIME) }.collect(Collectors.toList())
            val maxTime = Collections.max(dateList)
            val latestTime = maxTime.format(DateTimeFormatter.ofPattern(TimeUtil.FORMAT))
            remoteTimeJsons.entrySet().forEach {
                if (it.value.asString == latestTime){
                    remoteDistTags.addProperty(LATEST, it.key)
                }
            }
            remoteTimeJsons.addProperty(MODIFIED, latestTime)
        }
        return if (remoteVersionsSet.isNotEmpty()) {
            remoteJson
        }else{
            logger.warn("package [$pkgName] with all versions migration failed!")
            null
        }
    }

    private fun installVersionAndTgzArtifact(
        context: ArtifactMigrateContext,
        remotePackageJson: JsonObject
    ): MigrateDetail {
        val name = remotePackageJson[NAME].asString
        //val migrationFailDataDetailInfo = MigrationFailDataDetailInfo(name, mutableSetOf())
        val packageList: MutableList<PackageMigrateDetail> = mutableListOf()
        val migrateDetail = MigrateDetail(context.projectId, context.repoName, packageList, description = "migration for package [$name]")

        val versions = remotePackageJson.getAsJsonObject(VERSIONS)
        var count = 0
        val totalSize = versions.keySet().size

        // 每个版本进行比对同步
        versions.keySet().forEach { version ->
            try {
                measureTimeMillis {
                    val versionJson = versions.getAsJsonObject(version)
                    val tarball = versionJson.getAsJsonObject(DIST).get(TARBALL).asString
                    storeVersionArtifact(context, versionJson)
                    storeTgzArtifact(context, tarball, name)
                }.apply {
                    logger.info(
                        "migrate npm package [$name] for version [$version] success, elapse $this ms.  process rate: [${++count}/${totalSize}]"
                    )
                }
            } catch (ignored: Exception) {
                logger.error("migrate package [$name] for version [$version] failed， message： ${ignored.message}")
                // delete version json file
                deleteVersionFile(context, name, version)
                //
                //migrationFailDataDetailInfo.versionSet.add(VersionFailDetail(version, ignored.message))
            }
        }
        // val failVersionSet = migrationFailDataDetailInfo.versionSet.stream().map { it.version }.collect(Collectors.toSet())
        // putPackageJsonFile(context, name, failVersionSet, remotePackageJson)
        return migrateDetail
    }

    private fun putPackageJsonFile(
        context: ArtifactMigrateContext,
        name: String,
        failVersionSet: Set<String>,
        remotePackageJson: JsonObject
    ) {
        // 查找最新版本的package.json文件
        var cacheArtifact: InputStream? = null
        context.putAttribute(NPM_FILE_FULL_PATH, String.format(NPM_PKG_METADATA_FULL_PATH, name))
        getCacheNodeInfo(context)?.let {
            cacheArtifact = getCacheArtifact(context)
        }
        val newPackageJson = (if (cacheArtifact != null) {
            val cachePackageJson = GsonUtils.transferInputStreamToJson(cacheArtifact!!)
            // 对比合并package.json，去除掉迁移失败的版本
            addPackageVersion(cachePackageJson, remotePackageJson, failVersionSet)
        } else {
            comparePackageJson(remotePackageJson, failVersionSet)
        }) ?: return
        // store package json file
        val newArtifactFile = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(newPackageJson))
        putArtifact(context, newArtifactFile)
        // 添加依赖
        npmDependentHandler.updatePkgDepts(
            context.userId, context.artifactInfo, newPackageJson, NpmOperationAction.MIGRATION
        )
        newArtifactFile.delete()
    }

    private fun deleteVersionFile(context: ArtifactMigrateContext, name: String, version: String) {
        val fullPath = String.format(NPM_PKG_VERSION_METADATA_FULL_PATH, name, name, version)
        with(context.artifactInfo) {
            if (nodeClient.exist(projectId, repoName, fullPath).data!!) {
                val nodeDeleteRequest = NodeDeleteRequest(projectId, repoName, fullPath, context.userId)
                nodeClient.delete(nodeDeleteRequest)
            }
        }
    }

    private fun storeVersionArtifact(context: ArtifactMigrateContext, versionJson: JsonObject) {
        val name = versionJson[NAME].asString
        val version = versionJson[VERSION].asString
        val versionArtifactFile = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(versionJson))
        val fullPath = String.format(NPM_PKG_VERSION_METADATA_FULL_PATH, name, name, version)
        context.putAttribute(NPM_FILE_FULL_PATH, fullPath)
        if (nodeClient.exist(context.artifactInfo.projectId, context.artifactInfo.repoName, fullPath).data!!) {
            logger.info(
                "package [$name] with version json file [$name-$version.json] " +
                    "is already exists in repository, skip migration."
            )
            return
        }
        val nodeCreateRequest = getNodeCreateRequest(context, versionArtifactFile)
        storageService.store(nodeCreateRequest.sha256!!, versionArtifactFile, context.storageCredentials)
        nodeClient.create(nodeCreateRequest)
        logger.info("migrate npm package [$name] with version json file [$name-$version.json] success.")
        versionArtifactFile.delete()
    }

    /**
     * 存储tgz文件
     */
    private fun storeTgzArtifact(context: ArtifactMigrateContext, tarball: String, name: String) {
        var response: Response? = null
        val tgzFilePath = tarball.substring(tarball.indexOf(name))
        context.putAttribute(NPM_FILE_FULL_PATH, "/$tgzFilePath")
        // hit cache continue
        getCacheArtifact(context)?.let {
            logger.info(
                "package [$name] with tgz file [$tgzFilePath] is already exists in repository, skip migration."
            )
            return
        }
        try {
            measureTimeMillis {
                response = okHttpUtil.doGet(tarball)
                if (checkResponse(response!!)) {
                    val artifactFile = createTempFile(response?.body()!!)
                    putArtifact(context, artifactFile)
                    artifactFile.delete()
                }
            }.apply {
                logger.info(
                    "migrate npm package [$name] with tgz file [${tgzFilePath.substringAfter('/')}] success, elapse $this ms."
                )
            }
        } catch (exception: IOException) {
            logger.error("http send url [$tarball] for artifact [$tgzFilePath] failed : $exception")
            throw exception
        } finally {
            response?.body()?.close()
        }
    }

    private fun getCacheArtifact(context: ArtifactContext): ArtifactInputStream? {
        val repositoryDetail = context.repositoryDetail
        val fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!
        val node = nodeClient.detail(repositoryDetail.projectId, repositoryDetail.name, fullPath).data
        if (node == null || node.folder) return null
        val inputStream =
            storageService.load(node.sha256!!, Range.full(node.size), context.storageCredentials)
        inputStream?.let { logger.debug("Cached remote artifact[$fullPath] is hit") }
        return inputStream
    }

    private fun getCacheNodeInfo(context: ArtifactContext): NodeDetail? {
        val repositoryDetail = context.repositoryDetail
        val fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!
        return nodeClient.detail(repositoryDetail.projectId, repositoryDetail.name, fullPath).data
    }

    private fun putArtifact(context: ArtifactMigrateContext, artifactFile: ArtifactFile) {
        val nodeCreateRequest = getNodeCreateRequest(context, artifactFile)
        storageService.store(nodeCreateRequest.sha256!!, artifactFile, context.storageCredentials)
        nodeClient.create(nodeCreateRequest)
    }

    private fun getNodeCreateRequest(context: ArtifactContext, file: ArtifactFile): NodeCreateRequest {
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

    /**
     * 创建临时文件并将响应体写入文件
     */
    private fun createTempFile(body: ResponseBody): ArtifactFile {
        return ArtifactFileFactory.build(body.byteStream())
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

    fun dependentMigrate(context: ArtifactMigrateContext) {
        val pkgJsonFile = searchPackageJsonFile(context)
        npmDependentHandler.updatePkgDepts(
            context.userId,
            context.artifactInfo,
            pkgJsonFile,
            NpmOperationAction.MIGRATION
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmLocalRepository::class.java)
    }
}
