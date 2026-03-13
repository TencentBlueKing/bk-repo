package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.model.TFederationGroup
import com.tencent.bkrepo.replication.pojo.federation.FederationGroupInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederationGroupCreateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationGroupUpdateRequest

interface FederationGroupService {
    fun listAutoEnableGroups(projectId: String): List<TFederationGroup>
    fun save(group: TFederationGroup): TFederationGroup
    fun findByName(name: String): TFederationGroup?
    fun listAll(): List<TFederationGroup>

    fun create(request: FederationGroupCreateRequest, operator: String): FederationGroupInfo
    fun update(request: FederationGroupUpdateRequest, operator: String): FederationGroupInfo
    fun delete(id: String)
    fun getById(id: String): FederationGroupInfo?
    fun list(): List<FederationGroupInfo>
}
