package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ApiUserResource
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_NAME
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiUserResourceImpl @Autowired constructor(
    private val userService: UserService,
    private val roleService: RoleService
) : ApiUserResource {

    override fun createUser(request: CreateUserRequest): Response<Boolean> {
        userService.createUser(request)
        return ResponseBuilder.success(true)
    }

    override fun createUserToProject(request: CreateUserToProjectRequest): Response<Boolean> {
        userService.createUserToProject(request)
        val createRoleRequest =
            CreateRoleRequest(PROJECT_MANAGE_ID, PROJECT_MANAGE_NAME, RoleType.PROJECT, request.projectId, null, true)
        val roleId = roleService.createRole(createRoleRequest)
        userService.addUserToRole(request.userId, roleId!!)
        return ResponseBuilder.success(true)
    }

    override fun listUser(rids: List<String>): Response<List<User>> {
        val result = userService.listUser(rids)
        return ResponseBuilder.success(result)
    }

    override fun deleteById(uid: String): Response<Boolean> {
        userService.deleteById(uid)
        return ResponseBuilder.success(true)
    }

    override fun detail(uid: String): Response<User?> {
        return ResponseBuilder.success(userService.getUserById(uid))
    }

    override fun updateById(uid: String, request: UpdateUserRequest): Response<Boolean> {
        userService.updateUserById(uid, request)
        return ResponseBuilder.success(true)
    }

    override fun addUserRole(uid: String, rid: String): Response<User?> {
        val result = userService.addUserToRole(uid, rid)
        return ResponseBuilder.success(result)
    }

    override fun removeUserRole(uid: String, rid: String): Response<User?> {
        val result = userService.removeUserFromRole(uid, rid)
        return ResponseBuilder.success(result)
    }

    override fun addUserRoleBatch(rid: String, request: List<String>): Response<Boolean> {
        userService.addUserToRoleBatch(request, rid)
        return ResponseBuilder.success(true)
    }

    override fun deleteUserRoleBatch(rid: String, request: List<String>): Response<Boolean> {
        userService.removeUserFromRoleBatch(request, rid)
        return ResponseBuilder.success(true)
    }

    override fun createToken(uid: String): Response<User?> {
        val result = userService.createToken(uid)
        return ResponseBuilder.success(result)
    }

    override fun addUserToken(uid: String, token: String): Response<User?> {
        val result = userService.addUserToken(uid, token)
        return ResponseBuilder.success(result)
    }

    override fun deleteToken(uid: String, token: String): Response<User?> {
        val result = userService.removeToken(uid, token)
        return ResponseBuilder.success(result)
    }

    override fun checkUserToken(uid: String, token: String): Response<Boolean> {
        userService.findUserByUserToken(uid, token) ?: return ResponseBuilder.success(false)
        return ResponseBuilder.success(true)
    }
}
