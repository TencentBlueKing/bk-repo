package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.model.TProject
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface PermissionRepository : MongoRepository<TPermission, String> {

}
