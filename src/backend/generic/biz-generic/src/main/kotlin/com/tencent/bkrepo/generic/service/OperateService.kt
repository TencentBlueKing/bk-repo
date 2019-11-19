package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.FileSizeInfo
import com.tencent.bkrepo.generic.pojo.operate.FileCopyRequest
import com.tencent.bkrepo.generic.pojo.operate.FileMoveRequest
import com.tencent.bkrepo.generic.pojo.operate.FileRenameRequest
import com.tencent.bkrepo.generic.pojo.operate.FileSearchRequest
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeSearchRequest
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 文件操作服务类
 *
 * @author: carrypan
 * @date: 2019-10-13
 */
@Service
class OperateService(
    private val nodeResource: NodeResource,
    private val permissionService: PermissionService
) {
    fun listFile(userId: String, projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): List<FileInfo> {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))

        return nodeResource.list(projectId, repoName, path, includeFolder, deep).data?.map { toFileInfo(it) } ?: emptyList()
    }

    fun searchFile(userId: String, request: FileSearchRequest): Page<FileInfo> {
        request.repoNameList.forEach {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, request.projectId, it))
        }

        val searchRequest = with(request) {
            NodeSearchRequest(
                projectId = projectId,
                repoNameList = repoNameList,
                pathPattern = pathPattern,
                metadataCondition = metadataCondition,
                page = page,
                size = size
            )
        }
        val nodePage = nodeResource.search(searchRequest).data
        val records = nodePage?.records?.map { toFileInfo(it) } ?: emptyList()
        return Page(request.page, request.size, nodePage?.count ?: 0, records)
    }

    fun getFileDetail(userId: String, projectId: String, repoName: String, fullPath: String): FileDetail {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))

        val nodeDetail = nodeResource.queryDetail(projectId, repoName, fullPath).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        return FileDetail(toFileInfo(nodeDetail.nodeInfo), nodeDetail.metadata)
    }

    fun getFileSize(userId: String, projectId: String, repoName: String, fullPath: String): FileSizeInfo {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))

        val nodeSizeInfo = nodeResource.getSize(projectId, repoName, fullPath).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        return FileSizeInfo(subFileCount = nodeSizeInfo.subNodeCount, size = nodeSizeInfo.size)
    }

    fun mkdir(userId: String, projectId: String, repoName: String, fullPath: String) {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))

        val fullUri = "$projectId/$repoName/$fullPath"
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
            logger.warn("user[$userId] mkdirs [$fullUri] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }

        logger.info("user[$userId] mkdirs [$fullUri] success")
    }

    fun delete(userId: String, projectId: String, repoName: String, fullPath: String) {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName))

        val fullUri = "$projectId/$repoName/$fullPath"
        val deleteRequest = NodeDeleteRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            operator = userId
        )
        val result = nodeResource.delete(deleteRequest)

        if (result.isNotOk()) {
            logger.warn("user[$userId] delete [$fullUri] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }

        logger.info("user[$userId] delete [$fullUri] success")
    }

    fun rename(userId: String, request: FileRenameRequest) {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, request.projectId, request.repoName))

        val fullUri = "${request.projectId}/${request.repoName}/${request.fullPath}"
        val renameRequest = with(request) {
            NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newFullPath = newFullPath,
                operator = userId
            )
        }
        val result = nodeResource.rename(renameRequest)
        if (result.isNotOk()) {
            logger.warn("user[$userId] rename [$fullUri] to [${request.newFullPath}] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
        logger.info("user[$userId] rename [$fullUri] to [${request.newFullPath}] success")
    }

    fun move(userId: String, request: FileMoveRequest) {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, request.srcProjectId, request.srcRepoName))
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, request.destProjectId, request.destRepoName))

        val srcUri = "${request.srcProjectId}/${request.srcRepoName}/${request.srcFullPath}"
        val destUri = "${request.destProjectId}/${request.destRepoName}/${request.destPath}"
        val moveRequest = with(request) {
            NodeMoveRequest(
                srcProjectId = srcProjectId,
                srcRepoName = srcRepoName,
                srcFullPath = srcFullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destPath = destPath,
                overwrite = overwrite,
                operator = userId
            )
        }

        val result = nodeResource.move(moveRequest)
        if (result.isNotOk()) {
            logger.warn("user[$userId] move [$srcUri] to [$destUri] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
        logger.info("user[$userId] move [$srcUri] to [$destUri] success")
    }

    fun copy(userId: String, request: FileCopyRequest) {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, request.srcProjectId, request.srcRepoName))
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, request.destProjectId, request.destRepoName))

        val srcUri = "${request.srcProjectId}/${request.srcRepoName}/${request.srcFullPath}"
        val destUri = "${request.destProjectId}/${request.destRepoName}/${request.destPath}"
        val copyRequest = with(request) {
            NodeCopyRequest(
                srcProjectId = srcProjectId,
                srcRepoName = srcRepoName,
                srcFullPath = srcFullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destPath = destPath,
                overwrite = overwrite,
                operator = userId
            )
        }

        val result = nodeResource.copy(copyRequest)
        if (result.isNotOk()) {
            logger.warn("user[$userId] copy [$srcUri] to [$destUri] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
        logger.info("user[$userId] copy [$srcUri] to [$destUri] success")
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
