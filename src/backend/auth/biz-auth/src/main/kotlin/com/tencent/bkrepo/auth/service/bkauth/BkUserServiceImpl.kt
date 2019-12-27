package com.tencent.bkrepo.auth.service.bkauth

import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "bkauth")
class BkUserServiceImpl @Autowired constructor(
    private val userRepository: UserRepository
) {
    fun createUser(request: CreateUserRequest) {
        throw ErrorCodeException(CommonMessageCode.OPERATION_UNSUPPORTED)
    }

    fun deleteById(name: String) {
        throw ErrorCodeException(CommonMessageCode.OPERATION_UNSUPPORTED)
    }

    fun getById(name: String): User? {
        throw ErrorCodeException(CommonMessageCode.OPERATION_UNSUPPORTED)
    }

    fun checkAdmin(name: String): Boolean {
        // todo 对接权限中心
        return false
    }
}
