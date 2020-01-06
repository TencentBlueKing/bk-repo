package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceUserResourceImpl @Autowired constructor(
    private val userService: UserService
) : ServiceUserResource {
    override fun createUser(request: CreateUserRequest): Response<Boolean> {
        userService.createUser(request)
        return Response(true)
    }

    override fun deleteById(uid: String): Response<Boolean> {
        userService.deleteById(uid)
        return Response(true)
    }

    override fun detail(uid: String): Response<User?> {
        return Response(userService.getUserById(uid))
    }

    override fun updateById(uid: String, request: UpdateUserRequest): Response<Boolean> {
        userService.updateUserById(uid, request)
        return Response(true)
    }

    override fun addUserRole(uid: String, rid: String): Response<User?> {
        val result = userService.addUserToRole(uid, rid)
        return Response(result)
    }

    override fun removeUserRole(uid: String, rid: String): Response<User?> {
        val result = userService.removeUserFromRole(uid, rid)
        return Response(result)
    }

    override fun addUserRolePatch(rid: String, request: List<String>): Response<Boolean> {
        userService.addUserToRoleBatch(request, rid)
        return Response(true)
    }

    override fun deleteUserRolePatch(rid: String, request: List<String>): Response<Boolean> {
        userService.removeUserFromRoleBatch(request, rid)
        return Response(true)
    }

    override fun createToken(uid: String): Response<User?> {
        val result = userService.createToken(uid)
        return Response(result)
    }

    override fun deleteToken(uid: String, token: String): Response<User?> {
        val result = userService.removeToken(uid, token)
        return Response(result)
    }

    override fun checkUserToken(uid: String, token: String): Response<Boolean> {
        userService.findUserByUserToken(uid, token) ?: return Response(false)
        return Response(true)
    }
}
