package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.AddUserRoleRequest
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType

interface RoleService {
    fun listAll(): List<Role>

    fun listByType(roleType: RoleType): List<Role>

    fun addRole(request: CreateRoleRequest)

    fun deleteByName(name: String)

    fun addUserRole(request: AddUserRoleRequest)
}
