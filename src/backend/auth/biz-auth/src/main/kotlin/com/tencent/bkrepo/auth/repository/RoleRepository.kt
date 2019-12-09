package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RoleRepository : MongoRepository<TRole, String> {
    fun deleteByTypeAndRIdAndProjectId(type:RoleType,rId: String,projectId: String)
    fun findByType(type: String): List<TRole>
    fun findByTypeAndProjectId(type: RoleType, rId:String): List<TRole>?
}
