package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.User

interface UserService {
    fun getUserById(uId: String): User?


    fun createUser(request: CreateUserRequest): Boolean

    fun deleteById(uId: String): Boolean

    fun updateUserById(uId: String, request: UpdateUserRequest): Boolean

    fun addUserToRole(uId: String, rId: String): User?

    fun addUserToRoleBatch(IdList: List<String>, rId: String): Boolean

    fun removeUserFromRole(uId: String, rId: String): User?

    fun removeUserFromRoleBatch(IdList: List<String>, rId: String): Boolean

    fun createToken(uId: String): User?

    fun removeToken(uId: String, token: String): User?

    fun findUserByUserToken(uId: String, pwd: String): User?
}