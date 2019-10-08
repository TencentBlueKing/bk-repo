package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class UserServiceImpl @Autowired constructor(
    private val userRepository: UserRepository
) : UserService {
    override fun addUser(request: CreateUserRequest) {
        // todo 校验
        userRepository.insert(
            TUser(
                id = null,
                name = request.name,
                displayName = request.displayName,
                pwd = request.pwd,
                admin = request.admin,
                locked = false
            )
        )
    }

    override fun deleteByName(name: String) {
        userRepository.deleteByName(name)
    }

    override fun getByName(name: String): User? {
        val user = userRepository.findOneByName(name) ?: return null

        return User(
            id = user.id!!,
            name = user.name,
            displayName = user.displayName,
            pwd = user.pwd, // todo 密码加密解密
            admin = user.admin,
            locked = user.locked
        )
    }

    override fun checkAdmin(name: String): Boolean {
        val user = userRepository.findOneByName(name) ?: return false
        return user.admin
    }

    override fun getById(userId: String): User {
        return User(
            id = "user01",
            name = "user01",
            displayName = "user01",
            pwd = "",
            admin = true,
            locked = false
        )
    }


}