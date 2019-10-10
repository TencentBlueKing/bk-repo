package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_INVALID
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import java.lang.RuntimeException

@RestController
class ServicePermissionResourceImpl @Autowired constructor(
    private val permissionService: PermissionService,
    private val userService: UserService
) : ServicePermissionResource {
    override fun deletePermission(id: String): Response<Boolean> {
        permissionService.deletePermission(id)
        return Response(true)
    }

    override fun createPermission(request: CreatePermissionRequest): Response<Boolean> {
        // todo check request
        permissionService.createPermission(request)
        return Response(true)
    }

    override fun checkAdmin(name: String): Response<Boolean> {
        // todo check request
        return Response(userService.checkAdmin(name))
    }

    override fun checkPermission(request: CheckPermissionRequest): Response<Boolean> {
        checkRequest(request)
        return Response(permissionService.checkPermission(request))
    }

    override fun listPermission(resourceType: ResourceType?): Response<List<Permission>> {
        return Response(permissionService.listPermission(resourceType))
    }

    private fun checkRequest(request: CheckPermissionRequest) {
        with(request) {
            when (resourceType) {
                ResourceType.SYSTEM -> {

                }
                ResourceType.PROJECT -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(PARAMETER_INVALID, "projectId required")
                    }
                }
                ResourceType.REPO -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(PARAMETER_INVALID, "projectId required")
                    }
                    if (repoId.isNullOrBlank()) {
                        throw ErrorCodeException(PARAMETER_INVALID, "repoId required")
                    }
                }
                ResourceType.NODE -> {
                    if (projectId.isNullOrBlank()) {
                        throw ErrorCodeException(PARAMETER_INVALID, "node required")
                    }
                    if (repoId.isNullOrBlank()) {
                        throw ErrorCodeException(PARAMETER_INVALID, "repoId required")
                    }
                    if (node.isNullOrBlank()) {
                        throw ErrorCodeException(PARAMETER_INVALID, "node required")
                    }
                }
            }
        }
    }
}
