package com.tencent.bkrepo.auth.dao

import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
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
        val result = this.updateFirst(query, update)
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

    fun listPermissionByProject(permName: String, projectId: String?, resourceType: String): TPermission? {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::permName.name).`is`(permName),
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType)
            )
        )
        return this.findOne(query)
    }

    fun findOneByPermName(projectId: String, repoName: String, permName: String, resourceType: String): TPermission? {
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

    fun listByRoleAndResource(roles: List<String>, resourceType: String): List<TPermission> {
        val query = Query(
            Criteria(TPermission::roles.name).`in`(roles)
                .and(TPermission::resourceType.name).isEqualTo(resourceType)
        )
        return this.find(query)
    }

    fun listByRole(roles: List<String>): List<TPermission> {
        val query = Query(Criteria(TPermission::roles.name).`in`(roles))
        return this.find(query)
    }

    fun listByProjectIdAndUsers(projectId: String, userId: String): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::users.name).`is`(userId)
            )
        )
        return this.find(query)
    }


    fun listByProjectAndRoles(projectId: String, roles: List<String>): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::roles.name).`in`(roles)
            )
        )
        return this.find(query)
    }

    fun listByUserId(userId: String): List<TPermission> {
        val query = Query(Criteria(TPermission::users.name).`is`(userId))
        return this.find(query)
    }

    fun listByResourceAndRepo(resourceType: String, projectId: String, repoName: String): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType),
                Criteria.where(TPermission::repos.name).`is`(repoName)
            )
        )
        return this.find(query)
    }


    fun listByResourceAndProject(resourceType: String, projectId: String): List<TPermission> {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::projectId.name).`is`(projectId),
                Criteria.where(TPermission::resourceType.name).`is`(resourceType)
            )
        )
        return this.find(query)
    }

    fun listAllByProject(projectId: String): List<TPermission> {
        val query = Query.query(Criteria.where(TPermission::projectId.name).`is`(projectId))
        return this.find(query)
    }

    /**
     * 按 permName+projectId+resourceType 做原子 upsert（更新所有可变字段），用于联邦同步，避免 delete-then-create 间的鉴权空窗期。
     */
    fun upsertForFederation(perm: TPermission) {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TPermission::permName.name).`is`(perm.permName),
                Criteria.where(TPermission::projectId.name).`is`(perm.projectId),
                Criteria.where(TPermission::resourceType.name).`is`(perm.resourceType)
            )
        )
        val update = Update()
            .set(TPermission::repos.name, perm.repos)
            .set(TPermission::includePattern.name, perm.includePattern)
            .set(TPermission::excludePattern.name, perm.excludePattern)
            .set(TPermission::users.name, perm.users)
            .set(TPermission::roles.name, perm.roles)
            .set(TPermission::departments.name, perm.departments)
            .set(TPermission::actions.name, perm.actions)
            .set(TPermission::updatedBy.name, perm.updatedBy)
            .set(TPermission::updateAt.name, perm.updateAt)
            .setOnInsert(TPermission::permName.name, perm.permName)
            .setOnInsert(TPermission::projectId.name, perm.projectId)
            .setOnInsert(TPermission::resourceType.name, perm.resourceType)
            .setOnInsert(TPermission::createBy.name, perm.createBy)
            .setOnInsert(TPermission::createAt.name, perm.createAt)
        this.upsert(query, update)
    }

    fun listPermissionInRepo(
        projectId: String,
        repoName: String,
        uid: String,
        roles: List<String>
    ): List<TPermission> {
        val criteria = Criteria()
        val celeriac = criteria.orOperator(
            Criteria.where(TPermission::users.name).`is`(uid),
            Criteria.where(TPermission::roles.name).`in`(roles)
        ).and(TPermission::projectId.name).`is`(projectId).and(TPermission::repos.name).`is`(repoName)
        return this.find(Query.query(celeriac))
    }

    fun listInPermission(
        projectId: String,
        repoName: String,
        uid: String,
        resourceType: String,
        roles: List<String>
    ): List<TPermission> {
        val criteria = Criteria()
        val celeriac = criteria.orOperator(
            Criteria.where(TPermission::users.name).`is`(uid),
            Criteria.where(TPermission::roles.name).`in`(roles)
        ).and(TPermission::resourceType.name).`is`(resourceType).and(TPermission::projectId.name).`is`(projectId)
            .and(TPermission::repos.name).`is`(repoName)
        return this.find(Query.query(celeriac))
    }

    fun listNoPermission(
        projectId: String,
        repoName: String,
        uid: String,
        resourceType: String,
        roles: List<String>
    ): List<TPermission> {
        val criteria = Criteria()
        val celeriac = criteria.andOperator(
            Criteria.where(TPermission::users.name).ne(uid),
            Criteria.where(TPermission::roles.name).nin(roles)
        ).and(TPermission::resourceType.name).`is`(resourceType).and(TPermission::projectId.name).`is`(projectId)
            .and(TPermission::repos.name).`is`(repoName)
        return this.find(Query.query(celeriac))
    }
}