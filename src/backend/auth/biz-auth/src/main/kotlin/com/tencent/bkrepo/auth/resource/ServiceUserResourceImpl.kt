package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
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

    override fun updateById(uid: String,request: UpdateUserRequest): Response<Boolean> {
        userService.updateUserById(uid, request)
        return Response(true)
    }

    override fun addUserRole(uid: String, rid: String): Response<Boolean> {
        userService.addUserToRole(uid, rid)
        return Response(true)
    }

    override fun removeUserRole(uid: String, rid: String): Response<Boolean> {
        userService.removeUserFromRole(uid, rid)
        return Response(true)
    }

    override fun addUserRolePatch(rid: String, request: List<String>): Response<Boolean> {
        userService.addUserToRoleBatch(request, rid)
        return Response(true)
    }

    override fun deleteUserRolePatch(rid: String,request: List<String>): Response<Boolean> {
        userService.removeUserFromRoleBatch(request, rid)
        return Response(true)
    }

    override fun createToken(uid: String): Response<Boolean> {
        userService.createToken(uid)
        return Response(true)
    }

    override fun deleteToken(uid: String, token: String): Response<Boolean> {
        userService.removeToken(uid,token)
        return Response(true)
    }

    override fun checkUserToken(uid: String, token: String): Response<Boolean> {
        val result = userService.findUserByUserToken(uid,token) ?: return Response(false)
        return Response(true)
    }

}
