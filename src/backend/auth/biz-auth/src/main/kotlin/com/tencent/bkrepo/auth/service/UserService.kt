package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.User

interface UserService {

    fun getUserById(userId: String): User?

    fun createUser(request: CreateUserRequest): Boolean

    fun createUserToProject(request: CreateUserToProjectRequest): Boolean

    fun listUser(rids: List<String>): List<User>

    fun deleteById(userId: String): Boolean

    fun updateUserById(userId: String, request: UpdateUserRequest): Boolean

    fun addUserToRole(userId: String, roleId: String): User?

    fun addUserToRoleBatch(idList: List<String>, roleId: String): Boolean

    fun removeUserFromRole(userId: String, roleId: String): User?

    fun removeUserFromRoleBatch(idList: List<String>, roleId: String): Boolean

    fun createToken(userId: String): User?

    fun addUserToken(userId: String, token: String): User?

    fun removeToken(userId: String, token: String): User?

    fun findUserByUserToken(userId: String, pwd: String): User?
}
