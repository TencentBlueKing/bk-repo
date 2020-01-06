package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.operate.FileSearchRequest
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeSearchRequest
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

    companion object {

        fun toFileInfo(nodeInfo: NodeInfo): FileInfo {
            return nodeInfo.let {
                FileInfo(
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    folder = it.folder,
                    path = it.path,
                    name = it.name,
                    fullPath = it.fullPath,
                    size = it.size,
                    sha256 = it.sha256,
                    md5 = it.md5,
                    projectId = it.projectId,
                    repoName = it.repoName
                )
            }
        }
    }
}
