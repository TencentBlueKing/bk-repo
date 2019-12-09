package com.tencent.bkrepo.auth.service.bkauth

import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "bkauth")
class BkUserServiceImpl @Autowired constructor(
        private val userRepository: UserRepository
) {
    fun createUser(request: CreateUserRequest) {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    fun deleteById(name: String) {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    fun getById(name: String): User? {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    fun checkAdmin(name: String): Boolean {
        // todo 对接权限中心
        return false
    }
}