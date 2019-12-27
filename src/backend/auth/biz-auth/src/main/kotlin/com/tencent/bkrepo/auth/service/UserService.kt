package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.User

interface UserService {
    fun getUserById(userId: String): User?

    fun createUser(request: CreateUserRequest): Boolean

    fun deleteById(userId: String): Boolean

    fun updateUserById(userId: String, request: UpdateUserRequest): Boolean

    fun addUserToRole(userId: String, roleId: String): User?

    fun addUserToRoleBatch(IdList: List<String>, roleId: String): Boolean

    fun removeUserFromRole(userId: String, roleId: String): User?

    fun removeUserFromRoleBatch(IdList: List<String>, roleId: String): Boolean

    fun createToken(userId: String): User?

    fun removeToken(userId: String, token: String): User?

    fun findUserByUserToken(userId: String, pwd: String): User?
}
