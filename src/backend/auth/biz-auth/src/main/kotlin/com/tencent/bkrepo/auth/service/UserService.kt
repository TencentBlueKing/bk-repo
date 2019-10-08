package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.User

interface UserService {
    fun getById(id: String): User

    fun getByName(name: String): User?

    fun checkAdmin(name: String): Boolean

    fun addUser(request: CreateUserRequest)

    fun deleteByName(name: String)

    // fun updateUser()
}
