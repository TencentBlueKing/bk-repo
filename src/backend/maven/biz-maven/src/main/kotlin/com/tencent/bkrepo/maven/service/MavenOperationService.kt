package com.tencent.bkrepo.maven.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.maven.artifact.MavenDeleteArtifactInfo
import com.tencent.bkrepo.maven.constants.MAVEN_METADATA_FILE_NAME
import com.tencent.bkrepo.maven.constants.METADATA_KEY_ARTIFACT_ID
import com.tencent.bkrepo.maven.constants.METADATA_KEY_CLASSIFIER
import com.tencent.bkrepo.maven.constants.METADATA_KEY_GROUP_ID
import com.tencent.bkrepo.maven.constants.METADATA_KEY_PACKAGING
import com.tencent.bkrepo.maven.constants.METADATA_KEY_VERSION
import com.tencent.bkrepo.maven.constants.PACKAGE_KEY
import com.tencent.bkrepo.maven.constants.POM
import com.tencent.bkrepo.maven.constants.VERSION
import com.tencent.bkrepo.maven.enum.HashType
import com.tencent.bkrepo.maven.enum.MavenMessageCode
import com.tencent.bkrepo.maven.exception.MavenArtifactNotFoundException
import com.tencent.bkrepo.maven.exception.MavenBadRequestException
import com.tencent.bkrepo.maven.pojo.Basic
import com.tencent.bkrepo.maven.pojo.MavenArtifactVersionData
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.util.DigestUtils
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.mavenGAVC
import com.tencent.bkrepo.maven.util.MavenStringUtils.checksumType
import com.tencent.bkrepo.maven.util.MavenStringUtils.formatSeparator
import com.tencent.bkrepo.maven.util.MavenUtil
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.apache.commons.lang3.StringUtils
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class MavenOperationService(
    private val packageClient: PackageClient,
    private val nodeClient: NodeClient,
    private val stageClient: StageClient,
    private val mavenMetadataService: MavenMetadataService
) {

    fun queryVersionDetail(context: ArtifactQueryContext): MavenArtifactVersionData? {
        val packageKey = context.request.getParameter(PACKAGE_KEY)
        val version = context.request.getParameter(VERSION)
        val artifactId = packageKey.split(":").last()
        val groupId = packageKey.removePrefix("gav://").split(":")[0]
        with(context.artifactInfo) {
            val trueVersion = packageClient.findVersionByName(projectId, repoName, packageKey, version).data
                ?: throw MavenArtifactNotFoundException(
                    MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, "$packageKey/$version", getRepoIdentify()
                )
            val jarNode = nodeClient.getNodeDetail(projectId, repoName, trueVersion.contentPath!!).data ?: return null
            val stageTag = if (context.repositoryDetail.category != RepositoryCategory.REMOTE) {
                stageClient.query(projectId, repoName, packageKey, version).data
            } else null
            val packageVersion = packageClient.findVersionByName(projectId, repoName, packageKey, version).data
            val count = packageVersion?.downloads ?: 0
            val createdDate = packageVersion?.createdDate?.format(DateTimeFormatter.ISO_DATE_TIME)
                ?: jarNode.createdDate
            val lastModifiedDate = packageVersion?.lastModifiedDate?.format(DateTimeFormatter.ISO_DATE_TIME)
                ?: jarNode.lastModifiedDate
            val mavenArtifactBasic = Basic(
                groupId,
                artifactId,
                version,
                jarNode.size, jarNode.fullPath,
                jarNode.createdBy, createdDate,
                jarNode.lastModifiedBy, lastModifiedDate,
                count,
                jarNode.sha256,
                jarNode.md5,
                stageTag,
                null
            )
            return MavenArtifactVersionData(mavenArtifactBasic, packageVersion?.packageMetadata)
        }
    }

    fun resolveMavenArtifact(fileType: String, artifactFile: ArtifactFile): Triple<String, Boolean, Model?> {
        logger.info("File's type is $fileType")
        var packaging = fileType
        var mavenPomModel: Model? = null
        if (fileType == POM) {
            mavenPomModel = artifactFile.getInputStream().use { MavenXpp3Reader().read(it) }
            val verNotBlank = StringUtils.isNotBlank(mavenPomModel.version)
            val isPom = mavenPomModel.packaging.equals(POM, ignoreCase = true)
            if (!verNotBlank || !isPom) {
                packaging = mavenPomModel.packaging
            }
        }
        val isArtifact = (packaging == fileType)
        logger.info("Current file is artifact: $isArtifact")
        return Triple(packaging, isArtifact, mavenPomModel)
    }

    fun buildPackageDownloadRecord(projectId: String, repoName: String, fullPath: String): PackageDownloadRecord? {
        val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
        val packaging = node?.nodeMetadata?.find { it.key == METADATA_KEY_PACKAGING }?.value?.toString()
        if (packaging == null || node.name.endsWith(".$POM") && packaging != POM) {
            return null
        }
        val mavenGAVC = fullPath.mavenGAVC()
        val version = mavenGAVC.version
        val artifactId = mavenGAVC.artifactId
        val groupId = mavenGAVC.groupId.formatSeparator("/", ".")
        val packageKey = PackageKeys.ofGav(groupId, artifactId)
        return PackageDownloadRecord(projectId, repoName, packageKey, version)
    }

    fun createMavenVersion(context: ArtifactContext, mavenGAVC: MavenGAVC, fullPath: String, size: Long) {
        val metadata = mutableMapOf(
            METADATA_KEY_GROUP_ID to mavenGAVC.groupId,
            METADATA_KEY_ARTIFACT_ID to mavenGAVC.artifactId,
            METADATA_KEY_VERSION to mavenGAVC.version
        )
        try {
            mavenGAVC.classifier?.let { metadata[METADATA_KEY_CLASSIFIER] = it }
            packageClient.createVersion(
                PackageVersionCreateRequest(
                    context.projectId,
                    context.repoName,
                    packageName = mavenGAVC.artifactId,
                    packageKey = PackageKeys.ofGav(mavenGAVC.groupId, mavenGAVC.artifactId),
                    packageType = PackageType.MAVEN,
                    versionName = mavenGAVC.version,
                    size = size,
                    artifactPath = fullPath,
                    overwrite = true,
                    createdBy = context.userId,
                    packageMetadata = metadata.map { MetadataModel(key = it.key, value = it.value) }
                )
            )
        } catch (ignore: DuplicateKeyException) {
            logger.warn(
                "The package info has been created for version[${mavenGAVC.version}] " +
                        "and package[${mavenGAVC.artifactId}] in repo ${context.artifactInfo.getRepoIdentify()}"
            )
        }
    }

    /**
     * 删除构件的checksum文件
     */
    fun deleteArtifactCheckSums(
        projectId: String,
        repoName: String,
        userId: String,
        node: NodeDetail,
        typeArray: Array<HashType> = HashType.values()
    ) {
        for (hashType in typeArray) {
            val fullPath = "${node.fullPath}.${hashType.ext}"
            nodeClient.getNodeDetail(projectId, repoName, fullPath).data?.let {
                val request = NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    operator = userId
                )
                nodeClient.deleteNode(request)
            }
        }
    }

    fun createNodeMetaData(artifactFile: ArtifactFile): List<MetadataModel> {
        val md5 = artifactFile.getFileMd5()
        val sha1 = artifactFile.getFileSha1()
        val sha256 = artifactFile.getFileSha256()
        val sha512 = artifactFile.getInputStream().use {
            val bytes = it.readBytes()
            DigestUtils.sha512(bytes, 0, bytes.size)
        }
        return mutableMapOf(
            HashType.MD5.ext to md5,
            HashType.SHA1.ext to sha1,
            HashType.SHA256.ext to sha256,
            HashType.SHA512.ext to sha512
        ).map {
            MetadataModel(key = it.key, value = it.value)
        }.toMutableList()
    }

    fun deletePackage(artifactInfo: MavenDeleteArtifactInfo, userId: String) {
        with(artifactInfo) {
            packageClient.listAllVersion(projectId, repoName, packageName).data.orEmpty().forEach {
                removeVersion(artifactInfo, it, userId)
            }
            // 删除artifactId目录
            val url = MavenUtil.extractPath(packageName)
            logger.info("$url will be deleted in repo ${getRepoIdentify()}")
            val request = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = url,
                operator = userId
            )
            nodeClient.deleteNode(request)
        }
    }

    /**
     * 删除目录以及目录下的文件
     */
    fun folderRemoveHandler(context: ArtifactRemoveContext, node: NodeDetail): MavenDeleteArtifactInfo? {
        logger.info("Will try to delete folder ${node.fullPath} in repo ${context.artifactInfo.getRepoIdentify()}")
        val option = NodeListOption(pageNumber = 0, pageSize = 10, includeFolder = true, sort = true)
        val nodes = nodeClient.listNodePage(context.projectId, context.repoName, node.fullPath, option).data!!
        if (nodes.records.isEmpty()) {
            // 如果目录下没有任何节点，则删除当前目录并返回
            val request = NodeDeleteRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                fullPath = node.fullPath,
                operator = context.userId
            )
            nodeClient.deleteNode(request)
            return null
        }
        // 如果当前目录下级节点包含目录，当前目录可能是artifactId所在目录
        return if (nodes.records.first().folder) {
            // 判断当前目录是否是artifactId所在目录
            val packageKey = MavenUtil.extractPackageKey(node.fullPath)
            packageClient.findPackageByKey(context.projectId, context.repoName, packageKey).data
                ?: throw MavenBadRequestException(MavenMessageCode.MAVEN_ARTIFACT_DELETE, node.fullPath)
            val url = MavenUtil.extractPath(packageKey) + "/$MAVEN_METADATA_FILE_NAME"
            MavenDeleteArtifactInfo(
                projectId = context.projectId,
                repoName = context.repoName,
                packageName = packageKey,
                version = StringPool.EMPTY,
                artifactUri = url
            )
        } else {
            // 下一级没有目录，当前目录就是版本或者当前目录是artifactId目录（没有实际版本）
            val fullPath = node.fullPath.trimEnd('/') + "/$MAVEN_METADATA_FILE_NAME"
            val mavenGAVC = fullPath.mavenGAVC()
            var packageKey = PackageKeys.ofGav(mavenGAVC.groupId, mavenGAVC.artifactId)
            val url = MavenUtil.extractPath(packageKey) + "/$MAVEN_METADATA_FILE_NAME"
            var version = mavenGAVC.version
            packageClient.findVersionByName(
                projectId = context.projectId,
                repoName = context.repoName,
                packageKey = packageKey,
                version = mavenGAVC.version
            ).data ?: run {
                // 当前目录是artifactId目录（没有实际版本）
                packageKey = MavenUtil.extractPackageKey(node.fullPath)
                version = StringPool.EMPTY
            }
            MavenDeleteArtifactInfo(
                projectId = context.projectId,
                repoName = context.repoName,
                packageName = packageKey,
                version = version,
                artifactUri = url
            )
        }
    }

    /**
     * 删除单个节点
     */
    fun deleteNode(artifactInfo: ArtifactInfo, userId: String) {
        with(artifactInfo) {
            val fullPath = artifactInfo.getArtifactFullPath()
            logger.info("Will prepare to delete file $fullPath in repo ${artifactInfo.getRepoIdentify()} ")
            nodeClient.getNodeDetail(projectId, repoName, fullPath).data?.let {
                if (it.fullPath.checksumType() == null) {
                    deleteArtifactCheckSums(
                        projectId = projectId,
                        repoName = repoName,
                        userId = userId,
                        node = it
                    )
                }
                if (ArtifactContextHolder.getRepoDetail()?.category != RepositoryCategory.REMOTE) {
                    // 需要删除对应的metadata表记录
                    mavenMetadataService.delete(artifactInfo, it)
                }
                val request = NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    operator = userId
                )
                nodeClient.deleteNode(request)
            } ?: throw MavenArtifactNotFoundException(
                MavenMessageCode.MAVEN_ARTIFACT_NOT_FOUND, fullPath, "$projectId|$repoName"
            )
        }
    }

    /**
     * 删除[version] 对应的node节点也会一起删除
     */
    fun removeVersion(artifactInfo: ArtifactInfo, version: PackageVersion, userId: String) {
        with(artifactInfo as MavenDeleteArtifactInfo) {
            logger.info(
                "Will delete package $packageName version ${version.name} in repo ${getRepoIdentify()}"
            )
            packageClient.deleteVersion(projectId, repoName, packageName, version.name)
            val artifactPath = MavenUtil.extractPath(packageName) + "/${version.name}"
            if (ArtifactContextHolder.getRepoDetail()?.category != RepositoryCategory.REMOTE) {
                // 需要删除对应的metadata表记录
                val (artifactId, groupId) = MavenUtil.extractGroupIdAndArtifactId(packageName)
                val mavenGAVC = MavenGAVC(groupId, artifactId, version.name, null)
                mavenMetadataService.delete(artifactInfo, null, mavenGAVC)
            }
            val request = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactPath,
                operator = userId
            )
            nodeClient.deleteNode(request)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MavenOperationService::class.java)
    }
}
