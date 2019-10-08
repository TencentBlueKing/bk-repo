package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.PermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction

interface PermissionService {
    fun checkPermission(request: PermissionRequest): Boolean

    fun createPermission(request: CreatePermissionRequest)

    fun checkSystemPermission(userId: String, action: PermissionAction): Boolean

    fun checkProjectPermission(userId: String, projectId: String, action: PermissionAction): Boolean

    fun checkRepoPermission(userId: String, projectId: String, repoId: String, action: PermissionAction): Boolean

    fun checkNodePermission(userId: String, projectId: String, repoId: String, node: String, action: PermissionAction): Boolean
}
