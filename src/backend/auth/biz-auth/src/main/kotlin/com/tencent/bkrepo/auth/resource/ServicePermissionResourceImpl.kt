package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.PermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import java.lang.RuntimeException

@RestController
class ServicePermissionResourceImpl @Autowired constructor(
    private val permissionService: PermissionService,
    private val userService: UserService
) : ServicePermissionResource {
    override fun createPermission(createPermissionRequest: CreatePermissionRequest): Response<Boolean> {
        // todo check request
        permissionService.createPermission(createPermissionRequest)
        return Response(true)
    }

    override fun checkAdmin(name: String): Response<Boolean> {
        // todo check request
        return Response(userService.checkAdmin(name))
    }

    override fun checkPermission(permissionRequest: PermissionRequest): Response<Boolean> {
        checkRequest(permissionRequest)
        with(permissionRequest){
            return when(resourceType){
                ResourceType.SYSTEM -> Response(permissionService.checkSystemPermission(userId, action))
                ResourceType.PROJECT -> Response(permissionService.checkProjectPermission(userId, projectId!!, action))
                ResourceType.REPO -> Response(permissionService.checkRepoPermission(userId, projectId!!, repoId!!, action))
                ResourceType.NODE -> Response(permissionService.checkNodePermission(userId, projectId!!, repoId!!, node!!, action))
                else -> throw RuntimeException("unsupported resource type")
            }
        }
    }

    private fun checkRequest(permissionRequest: PermissionRequest) {
        with(permissionRequest) {
            when (resourceType) {
                ResourceType.SYSTEM -> {

                }
                ResourceType.PROJECT -> {
                    if (projectId.isNullOrBlank()) {
                        throw RuntimeException("projectId required")
                    }
                }
                ResourceType.REPO -> {
                    if (projectId.isNullOrBlank()) {
                        throw RuntimeException("projectId required")
                    }
                    if (repoId.isNullOrBlank()) {
                        throw RuntimeException("repoId required")
                    }
                }
                ResourceType.NODE -> {
                    if (projectId.isNullOrBlank()) {
                        throw RuntimeException("projectId required")
                    }
                    if (repoId.isNullOrBlank()) {
                        throw RuntimeException("repoId required")
                    }
                    if (node.isNullOrBlank()) {
                        throw RuntimeException("node required")
                    }
                }
            }
        }
    }
}
