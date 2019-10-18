package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.FileSizeInfo
import com.tencent.bkrepo.generic.pojo.operate.FileCopyRequest
import com.tencent.bkrepo.generic.pojo.operate.FileMoveRequest
import com.tencent.bkrepo.generic.pojo.operate.FileRenameRequest
import com.tencent.bkrepo.generic.pojo.operate.FileSearchRequest
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRenameRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

/**
 * 文件操作服务类
 *
 * @author: carrypan
 * @date: 2019-10-13
 */
@Service
class OperateService(
    private val nodeResource: NodeResource,
    private val authService: AuthService
) {
    fun listFile(userId: String, projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): List<FileInfo> {
        logger.info("listFile, projectId: $projectId, repoName: $repoName, path: $path, includeFolder: $includeFolder, deep: $deep")
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
        return nodeResource.list(projectId, repoName, path, includeFolder, deep).data?.map { toFileInfo(it) } ?: emptyList()
    }

    fun searchFile(userId: String, searchRequest: FileSearchRequest): List<FileInfo> {
        logger.info("searchFile, searchRequest: $searchRequest")
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, searchRequest.projectId, searchRequest.repoName))
        return nodeResource.search(
                searchRequest.let {
                    NodeSearchRequest(
                            projectId = it.projectId,
                            repoName = it.repoName,
                            pathPattern = it.pathPattern,
                            metadataCondition = it.metadataCondition
                    )
                }
        ).data?.map { toFileInfo(it) } ?: emptyList()
    }

    fun getFileDetail(userId: String, projectId: String, repoName: String, fullPath: String): FileDetail {
        logger.info("getFileDetail, projectId: $projectId, repoName: $repoName, fullPath, $fullPath")
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
        val nodeDetail = nodeResource.queryDetail(projectId, repoName, fullPath).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        return FileDetail(toFileInfo(nodeDetail.nodeInfo), nodeDetail.metadata)
    }

    fun getFileSize(userId: String, projectId: String, repoName: String, fullPath: String): FileSizeInfo {
        logger.info("getFileSize, projectId: $projectId, repoName: $repoName, fullPath, $fullPath")
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
        val nodeSizeInfo = nodeResource.getSize(projectId, repoName, fullPath).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        return FileSizeInfo(subFileCount = nodeSizeInfo.subNodeCount, size = nodeSizeInfo.size)
    }

    fun mkdir(userId: String, projectId: String, repoName: String, fullPath: String) {
        logger.info("mkdir, projectId: $projectId, repoName: $repoName, fullPath, $fullPath")
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
        val fullUrl = "$projectId/$repoName/$fullPath"
        val createRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = true,
                fullPath = fullPath,
                overwrite = false,
                operator = userId
        )
        val result = nodeResource.create(createRequest)

        if (result.isNotOk()) {
            logger.warn("user[$userId] mkdirs [$fullUrl] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }

        logger.info("user[$userId] mkdirs [$fullUrl] success")
    }

    fun delete(userId: String, projectId: String, repoName: String, fullPath: String) {
        logger.info("delete, projectId: $projectId, repoName: $repoName, fullPath, $fullPath")
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
        val fullUrl = "$projectId/$repoName/$fullPath"
        val deleteRequest = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                operator = userId
        )
        val result = nodeResource.delete(deleteRequest)

        if (result.isNotOk()) {
            logger.warn("user[$userId] delete [$fullUrl] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }

        logger.info("user[$userId] delete [$fullUrl] success")
    }

    fun rename(userId: String, projectId: String, repoName: String, fullPath: String, fileRenameRequest: FileRenameRequest) {
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))

        val fullUrl = "$projectId/$repoName/$fullPath"
        val newFullPath = fileRenameRequest.newFullPath
        val renameRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newFullPath = fileRenameRequest.newFullPath,
                operator = userId
        )
        val result = nodeResource.rename(renameRequest)

        if (result.isNotOk()) {
            logger.warn("user[$userId] rename [$fullUrl] to [$newFullPath] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }

        logger.info("user[$userId] rename [$fullUrl] to [$newFullPath] success")
    }

    fun move(userId: String, projectId: String, repoName: String, fullPath: String, fileMoveRequest: FileMoveRequest) {
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))

        val fullUrl = "$projectId/$repoName/$fullPath"
        val toPath = fileMoveRequest.toPath
        val renameRequest = NodeMoveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newFullPath = toPath,
                overwrite = fileMoveRequest.overwrite,
                operator = userId
        )
        val result = nodeResource.move(renameRequest)

        if (result.isNotOk()) {
            logger.warn("user[$userId] move [$fullUrl] to [$toPath] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }

        logger.info("user[$userId] move [$fullUrl] to [$toPath] success")
    }

    fun copy(userId: String, projectId: String, repoName: String, fullPath: String, fileCopyRequest: FileCopyRequest) {
        val toProjectId = fileCopyRequest.toProjectId
        val toRepoName = fileCopyRequest.toRepoName
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))
        authService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, toProjectId, toRepoName))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OperateService::class.java)

        fun toFileInfo(nodeInfo: NodeInfo): FileInfo {
            return nodeInfo.let { FileInfo(
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    folder = it.folder,
                    path = it.path,
                    name = it.name,
                    fullPath = it.fullPath,
                    size = it.size,
                    sha256 = it.sha256
            ) }
        }
    }
}
