package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType

interface RoleService {

    fun createRole(request: CreateRoleRequest): String?

    fun deleteRoleByid(id: String): Boolean

    fun listRoleByProject(type: RoleType?, projectId: String?): List<Role>

    fun detail(rid:String):Role?
}
