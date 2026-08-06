package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.replication.dao.FederationGroupDao
import com.tencent.bkrepo.replication.model.TFederationGroup
import com.tencent.bkrepo.replication.pojo.federation.FederationGroupInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederationGroupCreateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationGroupUpdateRequest
import com.tencent.bkrepo.replication.service.FederationGroupService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class FederationGroupServiceImpl(
    private val federationGroupDao: FederationGroupDao
) : FederationGroupService {

    override fun listAutoEnableGroups(projectId: String): List<TFederationGroup> {
        return federationGroupDao.findAutoEnableGroups(projectId)
    }

    override fun save(group: TFederationGroup): TFederationGroup {
        return federationGroupDao.save(group)
    }

    override fun findByName(name: String): TFederationGroup? {
        return federationGroupDao.findByName(name)
    }

    override fun listAll(): List<TFederationGroup> {
        return federationGroupDao.findAll()
    }

    override fun create(request: FederationGroupCreateRequest, operator: String): FederationGroupInfo {
        if (federationGroupDao.findByName(request.name) != null) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, request.name)
        }
        val now = LocalDateTime.now()
        val group = federationGroupDao.save(
            TFederationGroup(
                name = request.name,
                currentClusterId = request.currentClusterId,
                clusterIds = request.clusterIds,
                autoEnableForNewRepo = request.autoEnableForNewRepo,
                projectScope = request.projectScope,
                createdBy = operator,
                createdDate = now,
                lastModifiedBy = operator,
                lastModifiedDate = now,
            )
        )
        return group.toInfo()
    }

    override fun update(request: FederationGroupUpdateRequest, operator: String): FederationGroupInfo {
        val existing = federationGroupDao.findById(request.id)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, request.id)
        val updated = federationGroupDao.save(
            existing.copy(
                currentClusterId = request.currentClusterId ?: existing.currentClusterId,
                clusterIds = request.clusterIds ?: existing.clusterIds,
                autoEnableForNewRepo = request.autoEnableForNewRepo ?: existing.autoEnableForNewRepo,
                projectScope = if (request.projectScope != null) request.projectScope else existing.projectScope,
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now(),
            )
        )
        return updated.toInfo()
    }

    override fun delete(id: String) {
        federationGroupDao.findById(id)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        federationGroupDao.deleteById(id)
    }

    override fun getById(id: String): FederationGroupInfo? {
        return federationGroupDao.findById(id)?.toInfo()
    }

    override fun list(): List<FederationGroupInfo> {
        return federationGroupDao.findAll().map { it.toInfo() }
    }

    private fun TFederationGroup.toInfo() = FederationGroupInfo(
        id = id!!,
        name = name,
        currentClusterId = currentClusterId,
        clusterIds = clusterIds,
        autoEnableForNewRepo = autoEnableForNewRepo,
        projectScope = projectScope,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate,
    )
}
