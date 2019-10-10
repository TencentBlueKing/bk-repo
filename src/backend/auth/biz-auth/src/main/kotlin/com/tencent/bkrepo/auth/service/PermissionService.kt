package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.enums.ResourceType

interface PermissionService {
    fun checkPermission(request: CheckPermissionRequest): Boolean

    fun createPermission(request: CreatePermissionRequest)

    fun listPermission(resourceType: ResourceType?): List<Permission>

    fun deletePermission(id: String)
}
