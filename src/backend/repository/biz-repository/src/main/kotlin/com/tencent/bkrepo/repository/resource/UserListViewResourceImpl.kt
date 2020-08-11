package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.repository.api.UserListViewResource
import com.tencent.bkrepo.repository.service.ListViewService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class UserListViewResourceImpl @Autowired constructor(
    private val listViewService: ListViewService,
    private val permissionManager: PermissionManager
) : UserListViewResource {

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun listNodeView(artifactInfo: ArtifactInfo) {
        listViewService.listNodeView(artifactInfo)
    }

    @Principal(type = PrincipalType.ADMIN)
    override fun listProjectView() {
        listViewService.listProjectView()
    }

    override fun listRepositoryView(userId: String, projectId: String) {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.READ, projectId)
        listViewService.listRepoView(projectId)
    }
}
