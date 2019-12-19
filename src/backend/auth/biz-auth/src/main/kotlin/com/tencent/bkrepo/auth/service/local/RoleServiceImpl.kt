package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "local")
class RoleServiceImpl @Autowired constructor(
    private val roleRepository: RoleRepository
): RoleService {

    override fun listAllRole(): List<Role> {
        return roleRepository.findAll().map { transfer(it) }
    }

    override fun listRoleByType(type: String): List<Role> {
        return roleRepository.findByType(type).map { transfer(it) }
    }

    override fun createRole(request: CreateRoleRequest) :Boolean{
        val role = roleRepository.findOneByRIdAndProjectId(request.rid, request.projectId)
        if (role != null){
            logger.warn("create role [${request.rid} , ${request.projectId} ]  is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_RID)
        }

        roleRepository.insert(
            TRole(
                rId = request.rid,
                type = request.type,
                name = request.name,
                projectId = request.projectId
            )
        )
        return  false
    }

    override fun listRoleByProject(type: RoleType, projectId:String) :List<Role> {
        val tRole = roleRepository.findByTypeAndProjectId(type,projectId) ?: return emptyList()
        return  tRole.map { transfer(it) }
    }

    override fun deleteRoleByRid(type: RoleType, projectId:String,rid:String):Boolean {
        val role = roleRepository.deleteByTypeAndRIdAndProjectId(type , rid, projectId)
        if (role == 0L){
            logger.warn("delete role [$type ,$rid , $projectId  ]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_UID)
        }

        roleRepository.deleteByTypeAndRIdAndProjectId(type, rid, projectId)
        return true
    }

    private fun transfer(tRole: TRole): Role {
        return Role(
            id  = tRole.id,
            rId = tRole.rId,
            type = tRole.type,
            name = tRole.name,
            projectId = tRole.projectId
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RoleServiceImpl::class.java)
    }
}
