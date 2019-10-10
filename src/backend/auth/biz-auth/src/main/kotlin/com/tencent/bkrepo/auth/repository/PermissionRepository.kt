package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface PermissionRepository : MongoRepository<TPermission, String> {
    fun findByResourceType(resourceType: ResourceType): List<TPermission>
}
