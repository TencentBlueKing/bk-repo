package com.tencent.bkrepo.auth.service.inner

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TUserRole
import com.tencent.bkrepo.auth.pojo.AddUserRoleRequest
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.repository.ProjectRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.repository.UserRoleRepository
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_INVALID
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "inner")
class RoleServiceImpl @Autowired constructor(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val projectRepository: ProjectRepository,
    private val userRoleRepository: UserRoleRepository
): RoleService {
    override fun addUserRole(request: AddUserRoleRequest) {
        userRepository.findOneByName(request.userName)
            ?: throw ErrorCodeException(PARAMETER_INVALID, "invalid user name")
        val roleOp = roleRepository.findById(request.roleId)
        if(!roleOp.isPresent){
            throw ErrorCodeException(PARAMETER_INVALID, "role not found")
        }
        val projectOp = projectRepository.findById(request.projectId)
        if(!projectOp.isPresent){
            throw ErrorCodeException(PARAMETER_INVALID, "project not found")
        }
        // todo 校验 repo
        userRoleRepository.insert(
            TUserRole(
                id = null,
                userName = request.userName,
                roleId = request.roleId,
                projectId = request.projectId,
                repoId = request.repoId
            )
        )
    }

    override fun listAll(): List<Role> {
        return roleRepository.findAll().map { transfer(it) }
    }

    override fun listByType(roleType: RoleType): List<Role> {
        return roleRepository.findByRoleType(roleType).map { transfer(it) }
    }

    override fun addRole(request: CreateRoleRequest) {
        roleRepository.save(
            TRole(
                id = null,
                roleType = request.roleType,
                name = request.name,
                displayName = request.displayName
            )
        )
    }

    override fun deleteByName(name: String) {
        roleRepository.deleteByName(name)
    }

    private fun transfer(tRole: TRole): Role {
        return Role(
            id = tRole.id,
            roleType = tRole.roleType,
            name = tRole.name,
            displayName = tRole.displayName
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RoleServiceImpl::class.java)
    }
}
