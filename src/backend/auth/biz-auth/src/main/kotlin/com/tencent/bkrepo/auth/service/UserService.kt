package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.User

interface UserService {
    fun getByName(name: String): User?

    fun checkAdmin(name: String): Boolean

    fun createUser(request: CreateUserRequest)

    fun deleteByName(name: String)
}
