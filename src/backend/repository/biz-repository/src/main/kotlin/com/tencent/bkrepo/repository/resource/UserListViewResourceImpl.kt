package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.permission.PermissionService
import com.tencent.bkrepo.common.artifact.permission.PrincipalType
import com.tencent.bkrepo.repository.api.UserListViewResource
import com.tencent.bkrepo.repository.service.ListViewService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 *
 * @author: carrypan
 * @date: 2019/12/11
 */
@RestController
class UserListViewResourceImpl @Autowired constructor(
    private val listViewService: ListViewService,
    private val permissionService: PermissionService
) : UserListViewResource {

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun listNodeView(userId: String, artifactInfo: ArtifactInfo) {
        listViewService.listNodeView(artifactInfo)
    }

    override fun listProjectView(userId: String) {
        permissionService.checkPrincipal(userId, PrincipalType.ADMIN)
        listViewService.listRepoView()
    }

    override fun listRepositoryView(userId: String, projectId: String) {
        permissionService.checkPermission(userId, ResourceType.PROJECT, PermissionAction.READ, projectId)
        listViewService.listRepoView(projectId)
    }
}
