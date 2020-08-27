package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.repository.service.ListViewService
import io.swagger.annotations.Api
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("用户节点列表页")
@RestController
@RequestMapping("/api/list")
class UserListViewController(
    private val listViewService: ListViewService,
    private val permissionManager: PermissionManager
) {

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping(DEFAULT_MAPPING_URI)
    fun listNodeView(@ArtifactPathVariable artifactInfo: ArtifactInfo) {
        listViewService.listNodeView(artifactInfo)
    }

    @Principal(type = PrincipalType.ADMIN)
    @GetMapping
    fun listProjectView() {
        listViewService.listProjectView()
    }

    @GetMapping("/{projectId}")
    fun listRepositoryView(@RequestAttribute userId: String, @PathVariable projectId: String) {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.READ, projectId)
        listViewService.listRepoView(projectId)
    }
}
