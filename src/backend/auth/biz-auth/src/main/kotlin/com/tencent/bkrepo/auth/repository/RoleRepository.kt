package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RoleRepository : MongoRepository<TRole, String> {
    fun findByType(type: RoleType): List<TRole>
    fun findByProjectId(projectId: String): List<TRole>
    fun findByTypeAndProjectId(type: RoleType, projectId: String): List<TRole>
    fun findByRepoNameAndProjectId(repoName: String, projectId: String): List<TRole>
    fun findFirstByRoleIdAndProjectId(roleId: String, projectId: String): TRole?
    fun findFirstById(Id: String): TRole?
    fun findFirstByIdAndProjectIdAndType(Id: String, projectId: String, type: RoleType): TRole?
    fun findFirstByRoleIdAndProjectIdAndRepoName(RoleId: String, projectId: String, repoName: String): TRole?
    fun findFirstByIdAndProjectIdAndTypeAndRepoName(
        Id: String,
        projectId: String,
        type: RoleType,
        repoName: String
    ): TRole?
}
