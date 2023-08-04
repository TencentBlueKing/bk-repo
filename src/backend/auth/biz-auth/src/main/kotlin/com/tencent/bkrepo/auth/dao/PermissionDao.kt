package com.tencent.bkrepo.auth.dao

import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.util.query.PermissionQueryHelper
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository


@Repository
class PermissionDao : SimpleMongoDao<TPermission>() {

    private fun buildIdQuery(id: String): Query {
        return Query.query(Criteria.where("_id").`is`(id))
    }

    fun updateById(id: String, key: String, value: Any): Boolean {
        val update = Update()
        update.set(key, value)
        val result = this.upsert(buildIdQuery(id), update)
        if (result.matchedCount == 1L) return true
        return false
    }

    fun getPermissionByAction(
        projectId: String?,
        repoName: String?,
        uid: String,
        action: String,
        resourceType: String,
        roles: List<String>
    ): List<TPermission> {
        val query = PermissionQueryHelper.buildPermissionCheck(
            projectId, repoName, uid, action, resourceType, roles
        )
        return this.find(query, TPermission::class.java)
    }

    fun findFirstById(id: String): TPermission? {
        val query = buildIdQuery(id)
        return this.findOne(query)
    }

    fun deleteById(id: String): Boolean {
        val query = buildIdQuery(id)
        val result = this.remove(query)
        if (result.deletedCount == 1L) return true
        return false
    }

    fun findOneByPermNameAndProjectIdAndResourceType(
        permName: String,
        projectId: String?,
        resourceType: ResourceType
    ): TPermission? {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::permName.name).`is`(permName),
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType.toString())
            )
        )
        return this.findOne(query)
    }

    fun findByRolesIn(roles: List<String>): List<TPermission> {
        val query = Query(Criteria(TPermission::roles.name).`in`(roles))
        return this.find(query)
    }

    fun findByProjectIdAndUsers(projectId: String, userId: String): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::users.name).`is`(userId)
            )
        )
        return this.find(query)
    }


    fun findByProjectIdAndRolesIn(projectId: String, roles: List<String>): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::roles.name).`in`(roles)
            )
        )
        return this.find(query)
    }

    fun findByUsers(userId: String): List<TPermission> {
        val query = Query(Criteria(TPermission::users.name).`is`(userId))
        return this.find(query)
    }

    fun findByResourceTypeAndProjectIdAndRepos(
        resourceType: ResourceType,
        projectId: String,
        repoName: String
    ): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType.toString()),
                Criteria.where(TPermission::repos.name).`is`(repoName)
            )
        )
        return this.find(query)
    }

    fun findByResourceTypeAndProjectId(resourceType: ResourceType, projectId: String): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType.toString())
            )
        )
        return this.find(query)
    }
}