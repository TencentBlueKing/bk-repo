package com.tencent.bkrepo.generic.service

import com.google.common.io.ByteStreams
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.REPO_TYPE
import com.tencent.bkrepo.generic.pojo.artifactory.JfrogFile
import com.tencent.bkrepo.generic.pojo.artifactory.JfrogFilesData
import com.tencent.bkrepo.generic.util.UploadFileStoreUtils
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.metadata.MetadataUpsertRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 通用文件下载服务类
 *
 * @author: carrypan
 * @date: 2019-10-11
 */
@Service
class ArtifactoryService @Autowired constructor(
        private val permissionService: PermissionService,
        private val repositoryResource: RepositoryResource,
        private val nodeResource: NodeResource,
        private val metadataResource: MetadataResource,
        private val fileStorage: FileStorage
) {
    @Transactional(rollbackFor = [Throwable::class])
    fun upload(userId: String, projectId: String, repoName: String, fullPath: String, metadata: Map<String, String>, request: HttpServletRequest) {
        logger.info("upload, user: $userId, projectId: $projectId, repoName: $repoName, fullPath: $fullPath")
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
        val repository = repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data
            ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)

        var cacheFileFullPath: String? = null
        try{
            val cacheFileAndSize = UploadFileStoreUtils.storeFile(request.inputStream)
            cacheFileFullPath = cacheFileAndSize.first
            val cacheFileSize = cacheFileAndSize.second
            val calculatedSha256 = FileDigestUtils.fileSha256(listOf(File(cacheFileFullPath).inputStream()))

            // 保存节点
            val result = nodeResource.create(NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = fullPath,
                expires = 0,
                overwrite = true,
                size = cacheFileSize,
                sha256 = calculatedSha256,
                operator = userId
            ))

            if (result.isOk()) {
                val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
                fileStorage.store(calculatedSha256, File(cacheFileFullPath).inputStream(), storageCredentials)
            } else {
                logger.error("update file failed")
                throw ExternalErrorCodeException(result.code, result.message)
            }

            // 保存元数据
            val metadataResult = metadataResource.upsert(
                MetadataUpsertRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    metadata = metadata,
                    operator = userId
                )
            )
            if(metadataResult.isNotOk()){
                logger.error("upsert metadata failed")
                throw ExternalErrorCodeException(result.code, result.message)
            }
        } finally {
            if(cacheFileFullPath.isNullOrBlank()) File(cacheFileFullPath).delete()
        }
    }

    fun download(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse) {
        logger.info("upload, user: $userId, projectId: $projectId, repoName: $repoName, fullPath: $fullPath")
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))

        val repository = repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data
            ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)

        val node = nodeResource.queryDetail(projectId, repoName, fullPath).data
            ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)

        if (node.nodeInfo.folder) {
            throw ErrorCodeException(GenericMessageCode.DOWNLOAD_FOLDER_FORBIDDEN)
        }

        if (node.isBlockFile()) {
            throw ErrorCodeException(GenericMessageCode.DOWNLOAD_BLOCK_FORBIDDEN)
        }

        val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
        val file = fileStorage.load(node.nodeInfo.sha256!!, storageCredentials)
            ?: throw ErrorCodeException(GenericMessageCode.FILE_DATA_NOT_FOUND)

        response.addHeader("Content-Disposition", "attachment;filename=${node.nodeInfo.name}")
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
        ByteStreams.copy(file.inputStream(), response.outputStream)
    }

    fun listFile(userId: String, projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): JfrogFilesData {
        logger.info("listFile, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path, includeFolder: $includeFolder, deep: $deep")
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
        val dirDetail = nodeResource.queryDetail(projectId, repoName, path).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, path)
        val fileList = nodeResource.list(projectId, repoName, path, includeFolder, deep).data?.map { OperateService.toFileInfo(it) } ?: emptyList()
        return JfrogFilesData(
            uri = dirDetail.nodeInfo.fullPath,
            created = dirDetail.nodeInfo.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
            files = fileList.map {
                JfrogFile(
                    uri = it.fullPath,
                    size = it.size,
                    lastModified = it.lastModifiedDate,
                    folder = it.folder,
                    sha1 = ""
                )
            }
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactoryService::class.java)
    }
}
