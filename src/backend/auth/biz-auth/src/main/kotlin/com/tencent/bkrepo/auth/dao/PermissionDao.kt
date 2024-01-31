package com.tencent.bkrepo.auth.dao

import com.tencent.bkrepo.auth.model.TPermission
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
        val query = buildIdQuery(id)
        val result = this.updateFirst(query , update)
        if (result.matchedCount == 1L) return true
        return false
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

    fun checkPermissionInProject(
        permName: String,
        projectId: String?,
        resourceType: String
    ): TPermission? {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::permName.name).`is`(permName),
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType)
            )
        )
        return this.findOne(query)
    }

    fun findOnePermission(
        projectId: String,
        repoName: String,
        permName: String,
        resourceType: String
    ): TPermission? {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::permName.name).`is`(permName),
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::repos.name).`is`(repoName),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType)
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
        resourceType: String,
        projectId: String,
        repoName: String
    ): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType),
                Criteria.where(TPermission::repos.name).`is`(repoName)
            )
        )
        return this.find(query)
    }


    fun findByResourceTypeAndProjectId(resourceType: String, projectId: String): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType)
            )
        )
        return this.find(query)
    }

    fun inPermissionCheck(
        projectId: String?,
        repoName: String?,
        uid: String,
        resourceType: String,
        roles: List<String>
    ): List<TPermission> {
        val criteria = Criteria()
        var celeriac = criteria.orOperator(
            Criteria.where(TPermission::users.name).`is`(uid),
            Criteria.where(TPermission::roles.name).`in`(roles)
        ).and(TPermission::resourceType.name).`is`(resourceType)
        projectId?.let {
            celeriac = celeriac.and(TPermission::projectId.name).`is`(projectId)
        }
        repoName?.let {
            celeriac = celeriac.and(TPermission::repos.name).`is`(repoName)
        }
        return this.find(Query.query(celeriac))
    }

    fun noPermissionCheck(
        projectId: String?,
        repoName: String?,
        uid: String,
        resourceType: String,
        roles: List<String>
    ): List<TPermission>  {
        val criteria = Criteria()
        var celeriac = criteria.andOperator(
            Criteria.where(TPermission::users.name).ne(uid),
            Criteria.where(TPermission::roles.name).nin(roles)
        ).and(TPermission::resourceType.name).`is`(resourceType)
        projectId?.let {
            celeriac = celeriac.and(TPermission::projectId.name).`is`(projectId)
        }
        repoName?.let {
            celeriac = celeriac.and(TPermission::repos.name).`is`(repoName)
        }
        return this.find(Query.query(celeriac))
    }
}