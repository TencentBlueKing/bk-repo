package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TRolePermission
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RolePermissionRepository : MongoRepository<TRolePermission, String> {

}
