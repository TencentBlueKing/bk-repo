package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.pojo.AddUserRoleRequest
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.repository.ProjectRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.repository.UserRoleRepository
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "bkauth")
class BkRoleServiceImpl @Autowired constructor(
        private val userRepository: UserRepository,
        private val roleRepository: RoleRepository,
        private val projectRepository: ProjectRepository,
        private val userRoleRepository: UserRoleRepository
) {
    fun addUserRole(request: AddUserRoleRequest) {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    fun listAll(): List<Role> {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    fun listByType(roleType: RoleType): List<Role> {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    fun addRole(request: CreateRoleRequest) {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    fun deleteByName(name: String) {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkRoleServiceImpl::class.java)
    }
}
