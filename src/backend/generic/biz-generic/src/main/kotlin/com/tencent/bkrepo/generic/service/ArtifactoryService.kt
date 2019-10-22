package com.tencent.bkrepo.generic.service

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
import com.tencent.bkrepo.generic.constant.CONTENT_DISPOSITION_TEMPLATE
import com.tencent.bkrepo.generic.constant.DEFAULT_MIME_TYPE
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.REPO_TYPE
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import javax.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.server.MimeMappings
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import javax.servlet.http.HttpServletRequest

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
        private val fileStorage: FileStorage
) {
    @Transactional(rollbackFor = [Throwable::class])
    fun upload(userId: String, projectId: String, repoName: String, fullPath: String, request: HttpServletRequest) {
        logger.info("upload, user: $userId, projectId: $projectId, repoName: $repoName, fullPath: $fullPath")
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))

        request.inputStream

        // 参数格式校验
        expires.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "expires")
        file.takeUnless { it.isEmpty } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file")

        // 校验sha256
        val calculatedSha256 = FileDigestUtils.fileSha256(listOf(file.inputStream))
        if (sha256 != null && calculatedSha256 != sha256) {
            UploadService.logger.warn("user[$userId] simply upload file [$fullUri] failed: file sha256 verification failed")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sha256")
        }

        // 判断仓库是否存在
        val repository = repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId] simply upload file  [$fullUri] failed: $repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        }

        // 保存节点
        val result = nodeResource.create(NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = false,
            fullPath = fullPath,
            expires = 0,
            overwrite = true,
            size = file.size,
            sha256 = calculatedSha256,
            operator = userId
        ))

        if (result.isOk()) {
            val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
            fileStorage.store(calculatedSha256, file.inputStream, storageCredentials)
            UploadService.logger.info("user[$userId] simply upload file [$fullUri] success")
        } else {
            UploadService.logger.warn("user[$userId] simply upload file [$fullUri] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
    }

    fun simpleDownload(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse): ResponseEntity<InputStreamResource> {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))

        val formattedFullPath = NodeUtils.formatFullPath(fullPath)
        val fullUri = "$projectId/$repoName/$fullPath"
        // 查询repository
        val repository = repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId] simply download file  [$fullUri] failed: $repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        }

        // 查询节点
        val node = nodeResource.queryDetail(projectId, repoName, fullPath).data ?: run {
            logger.warn("user[$userId] simply download file [$fullUri] failed: $fullPath not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        }

        // 如果为目录
        if (node.nodeInfo.folder) {
            logger.warn("user[$userId] simply download file [$fullUri] failed: $fullPath not found")
            throw ErrorCodeException(GenericMessageCode.DOWNLOAD_FOLDER_FORBIDDEN)
        }

        // 如果为分块文件
        if (node.isBlockFile()) {
            logger.warn("user[$userId] simply download file [$fullUri] failed: $fullPath is a block file")
            throw ErrorCodeException(GenericMessageCode.DOWNLOAD_BLOCK_FORBIDDEN)
        }

        // fileStorage
        val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
        val inputStream = fileStorage.load(node.nodeInfo.sha256!!, storageCredentials) ?: run {
            logger.warn("user[$userId] simply download file [$fullUri] failed: file data not found")
            throw ErrorCodeException(GenericMessageCode.FILE_DATA_NOT_FOUND)
        }

        logger.info("user[$userId] simply download file [$fullUri] success.")
        return ResponseEntity
                .ok()
                .contentType(determineMediaType(formattedFullPath))
                .header(HttpHeaders.CONTENT_DISPOSITION, CONTENT_DISPOSITION_TEMPLATE.format(node.nodeInfo.name))
                .body(InputStreamResource(inputStream))
    }

    fun queryBlockInfo(userId: String, projectId: String, repoName: String, fullPath: String): List<BlockInfo> {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))

        val fullUri = "$projectId/$repoName/$fullPath"
        // 查询节点
        val node = nodeResource.queryDetail(projectId, repoName, fullPath).data ?: run {
            logger.warn("user[$userId] query file info [$fullUri] failed: $fullPath not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        }

        // 如果为目录或简单上传的文件则返回空列表
        node.takeIf { node.isBlockFile() }?.run {
            return node.blockList!!.map { BlockInfo(size = it.size, sha256 = it.sha256, sequence = it.sequence) }
        } ?: return emptyList()
    }

    fun blockDownload(userId: String, projectId: String, repoName: String, fullPath: String, sequence: Int, response: HttpServletResponse): ResponseEntity<InputStreamResource> {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))

        val fullUri = "$projectId/$repoName/$fullPath"
        val formattedFullPath = NodeUtils.formatFullPath(fullPath)
        // 查询仓库
        val repository = repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId] block download file [$fullUri] failed: $repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        }
        // 查询节点
        val node = nodeResource.queryDetail(projectId, repoName, fullPath).data ?: run {
            logger.warn("user[$userId] block download file [$fullUri] failed: $fullPath not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        }
        if (node.isBlockFile()) {
            node.blockList!!.find { it.sequence == sequence }?.run {
                // fileStorage
                val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
                val inputStream = fileStorage.load(this.sha256, storageCredentials) ?: run {
                    logger.warn("user[$userId] download block [$fullUri/$sequence] failed: file data not found")
                    throw ErrorCodeException(GenericMessageCode.FILE_DATA_NOT_FOUND)
                }

                logger.info("user[$userId] download block [$fullUri/$sequence] success.")
                return ResponseEntity
                        .ok()
                        .contentType(determineMediaType(formattedFullPath))
                        .header(HttpHeaders.CONTENT_DISPOSITION, CONTENT_DISPOSITION_TEMPLATE.format(node.nodeInfo.name))
                        .body(InputStreamResource(inputStream))
            } ?: run {
                logger.warn("user[$userId] download block [$fullUri/$sequence] failed: file data not found")
                throw ErrorCodeException(GenericMessageCode.FILE_DATA_NOT_FOUND)
            }
        } else {
            logger.warn("user[$userId] download block [$fullUri/$sequence] failed: $fullUri is a simple file")
            throw ErrorCodeException(GenericMessageCode.DOWNLOAD_SIMPLE_FORBIDDEN)
        }
    }

    private fun determineMediaType(fullPath: String): MediaType {
        val mimeType = MimeMappings.DEFAULT.get(NodeUtils.getExtension(fullPath)) ?: DEFAULT_MIME_TYPE
        return MediaType.parseMediaType(mimeType)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactoryService::class.java)
    }
}
