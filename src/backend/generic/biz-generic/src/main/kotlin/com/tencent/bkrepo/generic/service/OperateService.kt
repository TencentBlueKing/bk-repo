package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.springframework.stereotype.Service

/**
 * 文件操作服务类
 */
@Service
class OperateService(
    private val nodeClient: NodeClient,
    private val permissionManager: PermissionManager
) {

    fun listFile(userId: String, projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): List<FileInfo> {
        permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName)
        return nodeClient.list(projectId, repoName, path, includeFolder, deep).data?.map { toFileInfo(it) } ?: emptyList()
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
