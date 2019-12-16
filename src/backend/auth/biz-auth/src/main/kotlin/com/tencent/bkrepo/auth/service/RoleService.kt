package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType

interface RoleService {

    fun createRole(request: CreateRoleRequest):Boolean

    fun deleteRoleByRid(type: RoleType,projectId:String, rid:String):Boolean

    fun listAllRole(): List<Role>

    fun listRoleByType(type: String): List<Role>

    fun listRoleByProject(type: RoleType, projectId:String) :List<Role>


}
