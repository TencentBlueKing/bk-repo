package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServicePermissionResourceImpl @Autowired constructor(
    private val permissionService: PermissionService,
    private val userService: UserService
) : ServicePermissionResource {

    override fun createPermission(request: CreatePermissionRequest): Response<Boolean> {
        // todo check request

        return Response(permissionService.createPermission(request))
    }

    override fun checkAdmin(uid: String): Response<Boolean> {
        // todo check request
        val userInfo = userService.getUserById(uid) ?: return Response(false)
        if (userInfo.admin == false) {
            return Response(false)
        }
        return Response(true)
    }

    override fun checkPermission(request: CheckPermissionRequest): Response<Boolean> {
        checkRequest(request)
        return Response(permissionService.checkPermission(request))
    }

    override fun listPermission(resourceType: ResourceType?, projectId: String?): Response<List<Permission>> {
        return Response(permissionService.listPermission(resourceType, projectId))
    }

    override fun deletePermission(id: String): Response<Boolean> {
        return Response(permissionService.deletePermission(id))
    }

    override fun updateIncludePermissionPath(id: String, pathList: List<String>): Response<Boolean> {
        return Response(permissionService.updateIncludePath(id, pathList))
    }

    override fun updateExcludePermissionPath(id: String, pathList: List<String>): Response<Boolean> {
        return Response(permissionService.updateExcludePath(id, pathList))
    }

    override fun updatePermissionRepo(id: String, repoList: List<String>): Response<Boolean> {
        return Response(permissionService.updateRepoPermission(id, repoList))
    }

    override fun updatePermissionUser(id: String, uid: String, actionList: List<PermissionAction>): Response<Boolean> {
        return Response(permissionService.updateUserPermission(id, uid, actionList))
    }

    override fun removePermissionUser(id: String, uid: String): Response<Boolean> {
        return Response(permissionService.removeUserPermission(id, uid))
    }


    override fun updatePermissionRole(id: String, rid: String, actionList: List<PermissionAction>): Response<Boolean> {
        return Response(permissionService.updateRolePermission(id, rid, actionList))
    }

    override fun removePermissionRole(id: String, rid: String): Response<Boolean> {
        return Response(permissionService.removeRolePermission(id, rid))
    }


    private fun checkRequest(request: CheckPermissionRequest) {
        with(request) {
            when (resourceType) {
                ResourceType.SYSTEM -> {

                }
                ResourceType.PROJECT -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "projectId")
                    }
                }
                ResourceType.REPO -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "projectId")
                    }
                    if (repoName.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "repoId")
                    }
                }
                ResourceType.NODE -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "node")
                    }
                    if (repoName.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "repoId")
                    }
                    if (path.isNullOrBlank()) {
                        throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "node")
                    }
                }
            }
        }
    }
}
