package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.ListRepoPermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.RegisterResourceRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType

interface PermissionService {
    fun checkPermission(request: CheckPermissionRequest): Boolean

    fun listRepoPermission(request: ListRepoPermissionRequest): List<String>

    fun createPermission(request: CreatePermissionRequest): Boolean

    fun listPermission(resourceType: ResourceType?, projectId: String?, repoName: String?): List<Permission>

    fun deletePermission(id: String): Boolean

    fun updateIncludePath(id: String, path: List<String>): Boolean

    fun updateExcludePath(id: String, path: List<String>): Boolean

    fun updateRepoPermission(id: String, repos: List<String>): Boolean

    fun updateUserPermission(id: String, uid: String, actions: List<PermissionAction>): Boolean

    fun removeUserPermission(id: String, uid: String): Boolean

    fun updateRolePermission(id: String, rid: String, actions: List<PermissionAction>): Boolean

    fun removeRolePermission(id: String, rid: String): Boolean

    fun registerResource(request: RegisterResourceRequest)
}
