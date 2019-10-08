package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.repository.RoleRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class RoleService @Autowired constructor(
    private val roleRepository: RoleRepository,
    private val mongoTemplate: MongoTemplate
) {
    fun listAll(): List<TRole> {
        return roleRepository.findAll()
    }

    fun listByType(roleType: RoleType): List<TRole> {
        return roleRepository.findByRoleType(roleType)
    }

    fun addRole(createRoleRequest: CreateRoleRequest) {
        roleRepository.save(
            TRole(
                id = null,
                roleType = createRoleRequest.roleType,
                name = createRoleRequest.name,
                displayName = createRoleRequest.displayName
            )
        )
    }

    fun deleteByName(name: String) {
        roleRepository.deleteByName(name)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(RoleService::class.java)
    }
}
