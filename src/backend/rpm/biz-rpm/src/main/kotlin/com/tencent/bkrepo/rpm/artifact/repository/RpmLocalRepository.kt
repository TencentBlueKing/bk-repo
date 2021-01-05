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

package com.tencent.bkrepo.rpm.artifact.repository

<<<<<<< HEAD
import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
=======
import com.tencent.bkrepo.common.api.constant.StringPool.DOT
import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.UnsupportedMethodException
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
<<<<<<< HEAD
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
=======
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.*
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
<<<<<<< HEAD
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.rpm.INDEXER
import com.tencent.bkrepo.rpm.NO_INDEXER
import com.tencent.bkrepo.rpm.REPODATA
=======
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.GZ
import com.tencent.bkrepo.rpm.INDEXER
import com.tencent.bkrepo.rpm.NO_INDEXER
import com.tencent.bkrepo.rpm.REPODATA
import com.tencent.bkrepo.rpm.artifact.StorageManager
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import com.tencent.bkrepo.rpm.exception.RpmArtifactFormatNotSupportedException
import com.tencent.bkrepo.rpm.exception.RpmArtifactMetadataResolveException
import com.tencent.bkrepo.rpm.job.JobService
import com.tencent.bkrepo.rpm.pojo.ArtifactFormat
import com.tencent.bkrepo.rpm.pojo.ArtifactFormat.RPM
import com.tencent.bkrepo.rpm.pojo.ArtifactFormat.XML
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat
<<<<<<< HEAD
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.FULLPATH
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.FULLPATH_SHA256
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.NONE
import com.tencent.bkrepo.rpm.pojo.Basic
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.pojo.RepoDataPojo
import com.tencent.bkrepo.rpm.pojo.RpmArtifactVersionData
=======
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.DELETE
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.FULLPATH
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.FULLPATH_SHA256
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.NONE
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.pojo.RepodataUri
import com.tencent.bkrepo.rpm.pojo.RpmDeleteResponse
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import com.tencent.bkrepo.rpm.pojo.RpmRepoConf
import com.tencent.bkrepo.rpm.pojo.RpmUploadResponse
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.util.GZipUtils.gZip
<<<<<<< HEAD
import com.tencent.bkrepo.rpm.util.RpmConfiguration.toRpmRepoConf
import com.tencent.bkrepo.rpm.util.RpmHeaderUtils.getRpmBooleanHeader
import com.tencent.bkrepo.rpm.util.RpmVersionUtils
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toMetadata
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmPackagePojo
=======
import com.tencent.bkrepo.rpm.util.RpmHeaderUtils.getRpmBooleanHeader
import com.tencent.bkrepo.rpm.util.RpmVersionUtils
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toMetadata
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmVersion
import com.tencent.bkrepo.rpm.util.XmlStrUtils
import com.tencent.bkrepo.rpm.util.XmlStrUtils.getGroupNodeFullPath
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmMetadataUtils
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataFileList
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageFileList
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmXmlMetadata
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StopWatch
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.nio.channels.Channels

@Component
class RpmLocalRepository(
<<<<<<< HEAD
    private val stageClient: StageClient,
    private val jobService: JobService
) : LocalRepository() {

=======
    val storageManager: StorageManager
) : LocalRepository() {

    @Autowired
    private lateinit var jobService: JobService

>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    override fun onUploadBefore(context: ArtifactUploadContext) {
        super.onUploadBefore(context)
        val overwrite = HeaderUtils.getRpmBooleanHeader("X-BKREPO-OVERWRITE")
        if (!overwrite) {
            with(context.artifactInfo) {
<<<<<<< HEAD
                val node = nodeClient.getNodeDetail(projectId, repoName, getArtifactFullPath()).data
                if (node != null) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, getArtifactFullPath())
=======
                val node = nodeClient.detail(projectId, repoName, artifactUri).data
                if (node != null) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, artifactUri)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
                }
            }
        }
    }

<<<<<<< HEAD
    fun rpmNodeCreateRequest(context: ArtifactUploadContext, metadata: MutableMap<String, String>): NodeCreateRequest {
        val nodeCreateRequest = super.buildNodeCreateRequest(context)
=======
    private fun rpmNodeCreateRequest(context: ArtifactUploadContext, metadata: MutableMap<String, String>?): NodeCreateRequest {
        val nodeCreateRequest = super.getNodeCreateRequest(context)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        return nodeCreateRequest.copy(
            metadata = metadata,
            overwrite = true
        )
    }

    fun initMark(context: ArtifactSearchContext) {
        with(context.artifactInfo) {
            val nodeList = nodeClient.list(
                projectId, repoName, artifactUri,
                includeFolder = false, deep = true
            ).data?.filter {
                !it.path.contains("repodata") && it.name.endsWith(".rpm")
            }
            nodeList?.let {
                for (node in nodeList) {
                    val rpmLocation = node.fullPath.removePrefix("$artifactUri/")
                    initIndexMark(context, artifactUri, mutableMapOf(), IndexType.PRIMARY, rpmLocation)
                }
            }
        }
    }

    fun xmlIndexNodeCreate(
        userId: String,
        repositoryDetail: RepositoryDetail,
        fullPath: String,
        artifactFile: ArtifactFile,
        metadata: MutableMap<String, String>?
    ): NodeCreateRequest {
        val sha256 = artifactFile.getFileSha256()
        val md5 = artifactFile.getFileMd5()
        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            overwrite = true,
            fullPath = fullPath,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = userId,
            metadata = metadata
        )
    }

    /**
     * 查询rpm仓库属性
     */
<<<<<<< HEAD
    private fun getRpmRepoConf(context: ArtifactContext): RpmRepoConf {
        val rpmConfiguration = context.getCompositeConfiguration()
        return rpmConfiguration.toRpmRepoConf()
=======
    private fun getRpmRepoConf(context: ArtifactTransferContext): RpmRepoConf {
        val rpmConfiguration = context.repositoryInfo.configuration as RpmLocalConfiguration
        val repodataDepth = rpmConfiguration.repodataDepth ?: 0
        val enabledFileLists = rpmConfiguration.enabledFileLists ?: false
        val groupXmlSet = rpmConfiguration.groupXmlSet ?: mutableSetOf()
        return RpmRepoConf(repodataDepth, enabledFileLists, groupXmlSet)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    }

    /**
     * 检查请求uri地址的层级是否 > 仓库设置的repodata 深度
     * true 将会计算rpm包的索引
     * false 只提供文件服务器功能，返回提示信息
     */
    private fun checkNeedIndex(context: ArtifactUploadContext, repodataDepth: Int): Boolean {
<<<<<<< HEAD
        val depth = context.artifactInfo.getArtifactFullPath().removePrefix(SLASH).split(SLASH).size
=======
        val depth = context.artifactInfo.artifactUri.removePrefix(SLASH).split(SLASH).size
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        return depth > repodataDepth
    }

    /**
     * 生成并存储构件索引mark文件
     */
    private fun mark(context: ArtifactUploadContext, repeat: ArtifactRepeat, rpmRepoConf: RpmRepoConf): RpmVersion {
        val repodataDepth = rpmRepoConf.repodataDepth
<<<<<<< HEAD
        val repoDataPojo = XmlStrUtils.resolveRepodataUri(context.artifactInfo.getArtifactFullPath(), repodataDepth)
        val artifactFile = context.getArtifactFile()
        val artifactSha256 = context.getArtifactFile().getFileSha256()
        val sha1Digest = artifactFile.getInputStream().sha1()
        val artifactRelativePath = repoDataPojo.artifactRelativePath
=======
        val repodataUri = XmlStrUtils.resolveRepodataUri(context.artifactInfo.artifactUri, repodataDepth)
        val artifactFile = context.getArtifactFile()
        val sha1Digest = artifactFile.getInputStream().sha1()
        val artifactRelativePath = repodataUri.artifactRelativePath
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        val stopWatch = StopWatch("mark")

        stopWatch.start("getRpmFormat")
        val rpmFormat = Channels.newChannel(artifactFile.getInputStream()).use { RpmFormatUtils.resolveRpmFormat(it) }
        val rpmMetadata = RpmMetadataUtils.interpret(rpmFormat, artifactFile.getSize(), sha1Digest, artifactRelativePath)
        stopWatch.stop()
        val rpmVersion = RpmVersion(
            rpmMetadata.packages[0].name,
            rpmMetadata.packages[0].arch,
            rpmMetadata.packages[0].version.epoch.toString(),
            rpmMetadata.packages[0].version.ver,
            rpmMetadata.packages[0].version.rel
        )
        val markFileMatedata = rpmVersion.toMetadata()
        val othersIndexData = RpmMetadataChangeLog(
            listOf(
                RpmPackageChangeLog(
                    rpmMetadata.packages[0].checksum.checksum,
                    rpmMetadata.packages[0].name,
                    rpmMetadata.packages[0].version,
                    rpmMetadata.packages[0].format.changeLogs
                )
            ),
            1L
        )
        stopWatch.start("storeOthers")
<<<<<<< HEAD
        storeIndexMarkFile(context, repoDataPojo, repeat, markFileMatedata, IndexType.OTHERS, othersIndexData, artifactSha256)
=======
        storeIndexMarkFile(context, repodataUri, repeat, markFileMatedata, IndexType.OTHERS, othersIndexData)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        stopWatch.stop()
        if (rpmRepoConf.enabledFileLists) {
            val fileListsIndexData = RpmMetadataFileList(
                listOf(
                    RpmPackageFileList(
                        rpmMetadata.packages[0].checksum.checksum,
                        rpmMetadata.packages[0].name,
                        rpmMetadata.packages[0].version,
                        rpmMetadata.packages[0].format.files
                    )
                ),
                1L
            )
            stopWatch.start("storeFilelists")
<<<<<<< HEAD
            storeIndexMarkFile(context, repoDataPojo, repeat, markFileMatedata, IndexType.FILELISTS, fileListsIndexData, artifactSha256)
=======
            storeIndexMarkFile(context, repodataUri, repeat, markFileMatedata, IndexType.FILELISTS, fileListsIndexData)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
            stopWatch.stop()
        }

        rpmMetadata.filterRpmFileLists()
        rpmMetadata.packages[0].format.changeLogs.clear()
        stopWatch.start("storePrimary")
<<<<<<< HEAD
        storeIndexMarkFile(context, repoDataPojo, repeat, markFileMatedata, IndexType.PRIMARY, rpmMetadata, artifactSha256)
=======
        storeIndexMarkFile(context, repodataUri, repeat, markFileMatedata, IndexType.PRIMARY, rpmMetadata)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        stopWatch.stop()
        if (logger.isDebugEnabled) {
            logger.debug("markStat: $stopWatch")
        }
        return rpmVersion
<<<<<<< HEAD
=======
    }

    /**
     * 保存索引的时候新增一个标志文件。
     */
    private fun initIndexMark(
        context: ArtifactTransferContext,
        repodataPath: String,
        metadata: MutableMap<String, String>,
        indexType: IndexType,
        rpmLocation: String
    ) {
        val markFile = ArtifactFileFactory.build(ByteArrayInputStream("mark".toByteArray()))
        metadata["repeat"] = NONE.name
        val xmlFileNode = xmlIndexNodeCreate(
            context.userId,
            context.repositoryInfo,
            "/$repodataPath/$REPODATA/${indexType.value}/$rpmLocation",
            markFile,
            metadata
        )
        storageService.store(xmlFileNode.sha256!!, markFile, context.storageCredentials)
        with(xmlFileNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
        markFile.delete()
        nodeClient.create(xmlFileNode)
        logger.info("Success to insert $xmlFileNode")
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    }

    /**
     * 保存索引的时候新增一个标志文件。
<<<<<<< HEAD
     * [sha256] 构件sha256 值
     */
    private fun storeIndexMarkFile(
        context: ArtifactContext,
        repoData: RepoDataPojo,
        repeat: ArtifactRepeat,
        metadata: MutableMap<String, String>,
        indexType: IndexType,
        rpmXmlMetadata: RpmXmlMetadata? = null,
        sha256: String?
    ) {
        val repodataUri = repoData.repoDataPath
=======
     */
    private fun storeIndexMarkFile(
        context: ArtifactTransferContext,
        repodataUri: RepodataUri,
        repeat: ArtifactRepeat,
        metadata: MutableMap<String, String>,
        indexType: IndexType,
        rpmXmlMetadata: RpmXmlMetadata? = null
    ) {
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        logger.info("storeIndexMarkFile, repodataUri: $repodataUri, repeat: $repeat, indexType: $indexType, metadata: $metadata")
        val artifactFile = when (repeat) {
            FULLPATH_SHA256 -> {
                logger.warn("artifact repeat is $FULLPATH_SHA256, skip")
                return
            }
            NONE, FULLPATH -> {
                ArtifactFileFactory.build(ByteArrayInputStream(XmlStrUtils.toMarkFileXml(rpmXmlMetadata!!, indexType).toByteArray()))
            }
            else -> {
                ArtifactFileFactory.build(ByteArrayInputStream("mark".toByteArray()))
            }
        }
<<<<<<< HEAD
        metadata["repeat"] = repeat.name
        sha256?.let { metadata["sha256"] = it }
        val fullPath = repoData.getMarkPath(indexType)
        val markFileNode = xmlIndexNodeCreate(
            context.userId,
            context.repositoryDetail,
=======
        storageService.store(artifactFile.getFileSha256(), artifactFile, context.storageCredentials)
        val fullPath = "/${repodataUri.repodataPath}/$REPODATA/${indexType.value}/${repodataUri.artifactRelativePath}"
        metadata["repeat"] = repeat.name
        val markFileNode = xmlIndexNodeCreate(
            context.userId,
            context.repositoryInfo,
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
            fullPath,
            artifactFile,
            metadata
        )
<<<<<<< HEAD
        store(markFileNode, artifactFile, context.storageCredentials)
=======
        nodeClient.create(markFileNode)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        logger.info("mark file [${context.artifactInfo.projectId}|${context.artifactInfo.repoName}|$fullPath] created")
    }


    /**
     * 检查上传的构件是否已在仓库中，判断条件：uri && sha256
<<<<<<< HEAD
     * [ArtifactRepeat.FULLPATH_SHA256] 存在完全相同构件，不操作索引
     * [ArtifactRepeat.FULLPATH] 请求路径相同，但内容不同，更新索引
     * [ArtifactRepeat.NONE] 无重复构件
     */
    private fun checkRepeatArtifact(context: ArtifactUploadContext): ArtifactRepeat {
        val artifactUri = context.artifactInfo.getArtifactFullPath()
        val artifactSha256 = context.getArtifactSha256()
=======
     * 降低并发对索引文件的影响
     * ArtifactRepeat.FULLPATH_SHA256 存在完全相同构件，不操作索引
     * ArtifactRepeat.FULLPATH 请求路径相同，但内容不同，更新索引
     * ArtifactRepeat.NONE 无重复构件
     */
    private fun checkRepeatArtifact(context: ArtifactUploadContext): ArtifactRepeat {
        val artifactUri = context.artifactInfo.artifactUri
        val artifactSha256 = context.getArtifactFile().getFileSha256()

>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        return with(context.artifactInfo) {
            val node = nodeClient.getNodeDetail(projectId, repoName, artifactUri).data ?: return NONE
            if (node.sha256 == artifactSha256) {
                FULLPATH_SHA256
            } else {
                FULLPATH
            }
        }
    }

    private fun successUpload(context: ArtifactUploadContext, needIndex: Boolean, repodataDepth: Int) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        with(context.artifactInfo) {
            val description = if (needIndex) {
                INDEXER
            } else {
                String.format(NO_INDEXER, "$projectId/$repoName", repodataDepth, getArtifactFullPath())
            }
            val rpmUploadResponse = RpmUploadResponse(
                projectId, repoName, getArtifactFullPath(),
                context.getArtifactFile().getFileSha256(), context.getArtifactFile().getFileMd5(), description
            )
            response.writer.print(rpmUploadResponse.toJsonString())
        }
    }

<<<<<<< HEAD
=======
    private fun deleteFailed(context: ArtifactRemoveContext, description: String) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        with(context.artifactInfo) {
            val rpmUploadResponse = RpmDeleteResponse(projectId, repoName, artifactUri, description)
            response.writer.print(rpmUploadResponse.toJsonString())
        }
    }

>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    private fun getArtifactFormat(context: ArtifactUploadContext): ArtifactFormat {
        val format = context.artifactInfo.getArtifactFullPath()
            .split(SLASH).last().split(".").last()
        return when (format) {
            "xml" -> XML
            "rpm" -> RPM
            else -> {
                with(context.artifactInfo) { logger.info("$projectId/$repoName/${getArtifactFullPath()}: 格式不被接受") }
                throw RpmArtifactFormatNotSupportedException("rpm not supported `$format` artifact")
            }
        }
    }

    // 保存分组文件
    private fun storeGroupFile(context: ArtifactUploadContext) {
        val xmlByteArray = context.getArtifactFile().getInputStream().readBytes()
        val filename = context.artifactInfo.getArtifactFullPath().split("/").last()

        // 保存xml
        val xmlSha1 = context.getArtifactFile().getInputStream().sha1()
        val xmlSha1ArtifactFile = ArtifactFileFactory.build(xmlByteArray.inputStream())
        val metadata = mutableMapOf(
            "indexName" to filename,
            "indexType" to "group",
            "checksum" to xmlSha1,
            "size" to (xmlSha1ArtifactFile.getSize().toString()),
            "timestamp" to System.currentTimeMillis().toString()
        )
        val xmlNode = xmlIndexNodeCreate(
            context.userId,
            context.repositoryDetail,
            getGroupNodeFullPath(context.artifactInfo.getArtifactFullPath(), xmlSha1),
            xmlSha1ArtifactFile,
            metadata
        )
<<<<<<< HEAD
        store(xmlNode, xmlSha1ArtifactFile, context.storageCredentials)
=======
        storageManager.store(context, xmlNode, xmlSha1ArtifactFile)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4

        // 保存xml.gz
        val groupGZFile = xmlByteArray.gZip()
        try {
            val xmlGZFileSha1 = FileInputStream(groupGZFile).sha1()
            val groupGZArtifactFile = ArtifactFileFactory.build(FileInputStream(groupGZFile))
            val metadataGZ = mutableMapOf(
                "indexName" to "${filename}_gz",
                "indexType" to "group_gz",
                "checksum" to xmlGZFileSha1,
                "size" to (groupGZArtifactFile.getSize().toString()),
                "timestamp" to System.currentTimeMillis().toString()
            )
            val groupGZNode = xmlIndexNodeCreate(
                context.userId,
                context.repositoryDetail,
                getGroupNodeFullPath("${context.artifactInfo.getArtifactFullPath()}.gz", xmlGZFileSha1),
                groupGZArtifactFile,
                metadataGZ
            )
<<<<<<< HEAD
            store(groupGZNode, groupGZArtifactFile, context.storageCredentials)
=======
            storageManager.store(context, groupGZNode, groupGZArtifactFile)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        } finally {
            groupGZFile.delete()
        }

        // todo 删除多余节点
        flushRepoMdXML(context, null)
    }

    /**
     * 默认刷新匹配请求路径对应的repodata目录下的`repomd.xml`内容，
     * 当[repoDataPath]不为空时，刷新指定的[repoDataPath]目录下的`repomd.xml`内容
     */
<<<<<<< HEAD
    fun flushRepoMdXML(context: ArtifactContext, repoDataPath: String?) {
=======
    fun flushRepoMdXML(context: ArtifactTransferContext, repoDataPath: String?) {
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        logger.info("flushRepoMdXML: artifactInfo: ${context.artifactInfo}, repoDataPath: $repoDataPath")
        // 查询添加的groups
        val rpmRepoConf = getRpmRepoConf(context)
        val repodataDepth = rpmRepoConf.repodataDepth
        val indexPath = if (repoDataPath == null) {
<<<<<<< HEAD
            val repoDataPojo = XmlStrUtils.resolveRepodataUri(context.artifactInfo.getArtifactFullPath(), repodataDepth)
            "${repoDataPojo.repoDataPath}$REPODATA"
        } else {
            repoDataPath
        }
        jobService.flushRepoMdXML(context.repositoryDetail, indexPath)
=======
            val repodataUri = XmlStrUtils.resolveRepodataUri(context.artifactInfo.artifactUri, repodataDepth)
            "/${repodataUri.repodataPath}$REPODATA"
        } else {
            repoDataPath
        }
        jobService.flushRepoMdXML(context.repositoryInfo, indexPath)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun onUpload(context: ArtifactUploadContext) {
        val artifactFormat = getArtifactFormat(context)
        val rpmRepoConf = getRpmRepoConf(context)
        val needIndex: Boolean = checkNeedIndex(context, rpmRepoConf.repodataDepth)
        val repeat = checkRepeatArtifact(context)
<<<<<<< HEAD
        logger.info(
            "onUpload, artifactFormat: $artifactFormat, needIndex: $needIndex, repeat: $repeat, artifactUri: " +
                context.artifactInfo.getArtifactFullPath()
        )
=======
        logger.info("onUpload, artifactFormat: $artifactFormat, needIndex: $needIndex, repeat: $repeat, artifactUri: ${context.artifactInfo.artifactUri}")
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        if (repeat != FULLPATH_SHA256) {
            val nodeCreateRequest = if (needIndex) {
                when (artifactFormat) {
                    RPM -> {
<<<<<<< HEAD
                        // 保存rpm构件索引
                        val rpmVersion = mark(context, repeat, rpmRepoConf)
                        val metadata = rpmVersion.toMetadata()
                        metadata["action"] = repeat.name
                        val rpmPackagePojo = context.artifactInfo.getArtifactFullPath().toRpmPackagePojo()
                        // 保存包版本
                        val packageKey = PackageKeys.ofRpm(rpmPackagePojo.path, rpmPackagePojo.name)
                        packageClient.createVersion(
                            PackageVersionCreateRequest(
                                context.projectId,
                                context.repoName,
                                rpmPackagePojo.name,
                                packageKey,
                                PackageType.RPM,
                                null,
                                rpmPackagePojo.version,
                                context.getArtifactFile().getSize(),
                                null,
                                context.artifactInfo.getArtifactFullPath(),
                                overwrite = true,
                                createdBy = context.userId
                            )
                        )
                        metadata["packageKey"] = packageKey
                        metadata["version"] = rpmPackagePojo.version
=======
                        val rpmVersion = mark(context, repeat, rpmRepoConf)
                        val metadata = rpmVersion.toMetadata()
                        metadata["action"] = repeat.name
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
                        rpmNodeCreateRequest(context, metadata)
                    }
                    XML -> {
                        // 保存分组文件
                        storeGroupFile(context)
                        rpmNodeCreateRequest(context, mutableMapOf())
                    }
                }
<<<<<<< HEAD
            } else { rpmNodeCreateRequest(context, mutableMapOf()) }

            store(nodeCreateRequest, context.getArtifactFile(), context.storageCredentials)
        }
        successUpload(context, needIndex, rpmRepoConf.repodataDepth)
    }

    // rpm 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): DownloadStatisticsAddRequest? {
        with(context) {
            val fullPath = context.artifactInfo.getArtifactFullPath()
            return if (fullPath.endsWith(".rpm")) {
                val rpmPackagePojo = fullPath.toRpmPackagePojo()
                val packageKey = PackageKeys.ofRpm(rpmPackagePojo.path, rpmPackagePojo.name)
                return DownloadStatisticsAddRequest(
                    projectId, repoName,
                    packageKey, rpmPackagePojo.name, rpmPackagePojo.version
                )
            } else {
                null
            }
        }
=======
            } else {
                rpmNodeCreateRequest(context, mutableMapOf())
            }

            storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile(), context.storageCredentials)
            with(context.artifactInfo) { logger.info("Success to store $projectId/$repoName/$artifactUri") }
            nodeClient.create(nodeCreateRequest)
            logger.info("Success to insert $nodeCreateRequest")
            flushRepoMdXML(context, null)
        }
        successUpload(context, needIndex, rpmRepoConf.repodataDepth)
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    }

    /**
     * 返回包的版本数量
     */
    private fun getVersions(packageKey: String, context: ArtifactContext): Long? {
        return packageClient.findPackageByKey(
            context.projectId, context.repoName, packageKey
        ).data?.versions ?: return null
    }

    /**
     * 将构件在文件系统中的真实路径作为删除条件
     */
    @Transactional(rollbackFor = [Throwable::class])
    override fun remove(context: ArtifactRemoveContext) {
<<<<<<< HEAD
        val packageKey = HttpContextHolder.getRequest().getParameter("packageKey")
        if (!packageKey.isNullOrBlank()) {
            removeByPackageKey(packageKey, context)
        } else {
            removeByUrl(context)
        }
    }

    fun removeByUrl(context: ArtifactRemoveContext) {
        val fullPath = context.artifactInfo.getArtifactFullPath()
        val node = with(context) {
            nodeClient.getNodeDetail(projectId, repoName, fullPath).data
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND)
        }
        val metadata = node.metadata
        val packageKey = metadata["packageKey"] as String? ?: throw RpmArtifactMetadataResolveException(
            "${context.projectId} | ${context.repoName} | $fullPath: not found metadata.packageKey value"
        )
        val version = metadata["version"] as String? ?: throw RpmArtifactMetadataResolveException(
            "${context.projectId} | ${context.repoName} | $fullPath: not found metadata.version value"
        )
        removeRpmArtifact(node, packageKey, context, packageKey, version)
    }

    fun removeByPackageKey(packageKey: String, context: ArtifactRemoveContext) {
        val version = HttpContextHolder.getRequest().getParameter("version")
        if (version.isNullOrBlank()) {
            val versions = getVersions(packageKey, context)
            val pages = packageClient.listVersionPage(
                context.projectId,
                context.repoName,
                packageKey,
                VersionListOption(0, versions!!.toInt(), null, null)

            ).data?.records ?: return

            for (packageVersion in pages) {
                val artifactFullPath = packageVersion.contentPath!!
                val node = nodeClient.getNodeDetail(context.projectId, context.repoName, artifactFullPath).data
                    ?: continue
                removeRpmArtifact(node, artifactFullPath, context, packageKey, packageVersion.name)
=======
        with(context.artifactInfo) {
            val node = nodeClient.detail(projectId, repoName, artifactUri).data
            if (node == null) {
                deleteFailed(context, "not found")
                return
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
            }
        } else {
            with(context.artifactInfo) {
                val packageVersion = packageClient.findVersionByName(
                    context.projectId,
                    context.repoName,
                    packageKey,
                    version
                ).data ?: return
                val node = nodeClient.getNodeDetail(projectId, repoName, packageVersion.contentPath!!).data ?: return
                removeRpmArtifact(node, packageVersion.contentPath!!, context, packageKey, version)
            }
<<<<<<< HEAD
        }
    }

    fun removeRpmArtifact(
        node: NodeDetail,
        artifactFullPath: String,
        context: ArtifactRemoveContext,
        packageKey: String,
        version: String
    ) {
        if (node.folder) {
            throw UnsupportedMethodException("Delete folder is forbidden")
        }
        val nodeMetadata = node.metadata
        val artifactSha256 = node.sha256
        val rpmVersion = try {
            nodeMetadata.toRpmVersion(artifactFullPath)
        } catch (rpmArtifactMetadataResolveException: RpmArtifactMetadataResolveException) {
            logger.warn("$this not found metadata")
            RpmVersionUtils.resolverRpmVersion(artifactFullPath.split("/").last())
        }
        // 定位对应请求的索引目录
        val rpmRepoConf = getRpmRepoConf(context)
        val repodataDepth = rpmRepoConf.repodataDepth
        val repoData = XmlStrUtils.resolveRepodataUri(context.artifactInfo.getArtifactFullPath(), repodataDepth)

        // 更新 primary, others
        storeIndexMarkFile(
            context, repoData, ArtifactRepeat.DELETE, rpmVersion.toMetadata(), IndexType.PRIMARY, null, artifactSha256
        )
        storeIndexMarkFile(
            context, repoData, ArtifactRepeat.DELETE, rpmVersion.toMetadata(), IndexType.OTHERS, null, artifactSha256
        )
        if (rpmRepoConf.enabledFileLists) {
            storeIndexMarkFile(
                context, repoData, ArtifactRepeat.DELETE, rpmVersion.toMetadata(), IndexType.FILELISTS, null, artifactSha256
            )
        }
        with(context) {
            val nodeDeleteRequest = NodeDeleteRequest(projectId, repoName, artifactFullPath, context.userId)
            nodeClient.deleteNode(nodeDeleteRequest)
            logger.info("Success to delete node $nodeDeleteRequest")
            deleteVersion(projectId, repoName, packageKey, version)
            logger.info("Success to delete version $projectId | $repoName : $packageKey $version")
            flushRepoMdXML(context, null)
=======
            val nodeMetadata = node.metadata
            val artifactSha256 = node.sha256
            val rpmVersion = try {
                nodeMetadata.toRpmVersion(artifactUri)
            } catch (rpmArtifactMetadataResolveException: RpmArtifactMetadataResolveException) {
                logger.warn("$this not found metadata")
                RpmVersionUtils.resolverRpmVersion(artifactUri.split("/").last())
            }
            val artifactUri = context.artifactInfo.artifactUri
            // 定位对应请求的索引目录
            val rpmRepoConf = getRpmRepoConf(context)
            val repodataDepth = rpmRepoConf.repodataDepth
            val repodataUri = XmlStrUtils.resolveRepodataUri(context.artifactInfo.artifactUri, repodataDepth)

            storeIndexMarkFile(context, repodataUri, DELETE, rpmVersion.toMetadata(), IndexType.PRIMARY)
            storeIndexMarkFile(context, repodataUri, DELETE, rpmVersion.toMetadata(), IndexType.OTHERS)
            if (rpmRepoConf.enabledFileLists) {
                storeIndexMarkFile(context, repodataUri, DELETE, rpmVersion.toMetadata(), IndexType.FILELISTS)
            }

            val nodeDeleteRequest = NodeDeleteRequest(projectId, repoName, artifactUri, context.userId)
            nodeClient.delete(nodeDeleteRequest)
            logger.info("node [$projectId|$repoName|$artifactUri] deleted")
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        }
    }

    fun deleteVersion(projectId: String, repoName: String, packageKey: String, version: String) {
        packageClient.deleteVersion(projectId, repoName, packageKey, version)
        val page = packageClient.listVersionPage(projectId, repoName, packageKey).data ?: return
        if (page.records.isEmpty()) packageClient.deletePackage(projectId, repoName, packageKey)
    }

    /**
     * 刷新仓库下所有repodata
     */
    fun flushAllRepoData(context: ArtifactContext) {
        // 查询仓库索引层级
        val targetSet = jobService.findRepodataDirs(context.repositoryDetail)
        for (repoDataPath in targetSet) {
            flushRepoMdXML(context, repoDataPath)
        }
    }

    override fun query(context: ArtifactQueryContext): Any? {
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        val name = packageKey.split(":").last()
        val path = packageKey.removePrefix("rpm://").split(":")[0]
        val trueVersion = packageClient.findVersionByName(
            context.projectId,
            context.repoName,
            packageKey,
            version
        ).data ?: return null
        val artifactPath = trueVersion.contentPath ?: return null
        with(context.artifactInfo) {
            val jarNode = nodeClient.getNodeDetail(
                projectId, repoName, artifactPath
            ).data ?: return null
            val stageTag = stageClient.query(projectId, repoName, packageKey, version).data
            val rpmArtifactMetadata = jarNode.metadata
            val packageVersion = packageClient.findVersionByName(
                projectId, repoName, packageKey, version
            ).data
            val count = packageVersion?.downloads ?: 0
            val rpmArtifactBasic = Basic(
                path,
                name,
                version,
                jarNode.size, jarNode.fullPath,
                jarNode.createdBy, jarNode.createdDate,
                jarNode.lastModifiedBy, jarNode.lastModifiedDate,
                count,
                jarNode.sha256,
                jarNode.md5,
                stageTag,
                null
            )
            return RpmArtifactVersionData(rpmArtifactBasic, rpmArtifactMetadata)
        }
    }

<<<<<<< HEAD
    fun store(node: NodeCreateRequest, artifactFile: ArtifactFile, storageCredentials: StorageCredentials?) {
        storageManager.storeArtifactFile(node, artifactFile, storageCredentials)
        artifactFile.delete()
        with(node) { logger.info("Success to store$projectId/$repoName/$fullPath") }
        logger.info("Success to insert $node")
=======
    fun extList(context: ArtifactListContext, page: Int, size: Int): Page<String> {
        val artifactInfo = context.artifactInfo
        val artifactUri = artifactInfo.artifactUri
        val path = if(artifactUri.endsWith("/")) artifactUri else "$artifactUri/"
        val rule1 = mutableListOf<Rule>(
                Rule.QueryRule("projectId", artifactInfo.projectId, OperationType.EQ),
                Rule.QueryRule("repoName", artifactInfo.repoName, OperationType.EQ),
                Rule.QueryRule("path", path, OperationType.EQ),
                Rule.QueryRule("fullPath", path, OperationType.NE),
                Rule.QueryRule("name", "repodata", OperationType.NE)
        )
        val queryModel = QueryModel(
                page = PageLimit(page, size),
                sort = Sort(listOf("lastModifiedDate"), Sort.Direction.DESC),
                select = mutableListOf("name"),
                rule = Rule.NestedRule(rule1, Rule.NestedRule.RelationType.AND)
        )
        val pages = nodeClient.query(queryModel).data!!
        return Page(page, size, pages.count,  pages.records.map { it["name"] as String })
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RpmLocalRepository::class.java)
    }
}
