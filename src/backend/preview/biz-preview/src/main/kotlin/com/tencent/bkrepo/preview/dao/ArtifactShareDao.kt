package com.tencent.bkrepo.preview.dao

import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.preview.model.TArtifactShare
import com.tencent.bkrepo.preview.pojo.share.AccessibleShareChannel
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareKind
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareResourceType
import com.tencent.bkrepo.preview.pojo.share.ShareVisibility
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class ArtifactShareDao : PreviewSimpleMongoDao<TArtifactShare>() {

    fun findActiveByProjectRepoResourceId(
        projectId: String,
        repoName: String,
        resourceId: Long,
    ): TArtifactShare? {
        val query = Query(
            Criteria.where(TArtifactShare::projectId.name).isEqualTo(projectId)
                .and(TArtifactShare::repoName.name).isEqualTo(repoName)
                .and(TArtifactShare::resourceId.name).isEqualTo(resourceId)
                .and(TArtifactShare::shareKind.name).isEqualTo(ArtifactShareKind.MATERIAL)
                .and(TArtifactShare::resourceType.name).isEqualTo(ArtifactShareResourceType.DRIVE_NODE),
        )
        return findOne(query)
    }

    fun findActiveByResourceIds(
        projectId: String,
        repoName: String,
        resourceIds: Collection<Long>,
    ): List<TArtifactShare> {
        if (resourceIds.isEmpty()) {
            return emptyList()
        }
        val query = Query(
            Criteria.where(TArtifactShare::projectId.name).isEqualTo(projectId)
                .and(TArtifactShare::repoName.name).isEqualTo(repoName)
                .and(TArtifactShare::resourceId.name).`in`(resourceIds)
                .and(TArtifactShare::shareKind.name).isEqualTo(ArtifactShareKind.MATERIAL)
                .and(TArtifactShare::resourceType.name).isEqualTo(ArtifactShareResourceType.DRIVE_NODE),
        )
        return find(query)
    }

    /**
     * 按对外 shareId 查找。兼容 `_id` 为自定义字符串或 Mongo ObjectId 的历史数据。
     */
    fun findByShareId(shareId: String): TArtifactShare? {
        val id = shareId.trim()
        if (id.isEmpty()) {
            return null
        }
        findById(id)?.let { return it }
        if (!ObjectId.isValid(id)) {
            return null
        }
        return findOne(Query.query(Criteria.where(ID).isEqualTo(ObjectId(id))))
    }

    fun removeByProjectRepoResourceId(projectId: String, repoName: String, resourceId: Long) {
        val query = Query(
            Criteria.where(TArtifactShare::projectId.name).isEqualTo(projectId)
                .and(TArtifactShare::repoName.name).isEqualTo(repoName)
                .and(TArtifactShare::shareKind.name).isEqualTo(ArtifactShareKind.MATERIAL)
                .and(TArtifactShare::resourceType.name).isEqualTo(ArtifactShareResourceType.DRIVE_NODE)
                .and(TArtifactShare::resourceId.name).isEqualTo(resourceId),
        )
        remove(query)
    }

    fun listMine(
        createdBy: String,
        nameKeyword: String?,
        cursor: ArtifactShareListCursor?,
        limit: Int,
    ): List<TArtifactShare> {
        val criteria = combine(
            Criteria.where(TArtifactShare::createdBy.name).isEqualTo(createdBy)
                .and(TArtifactShare::shareKind.name).isEqualTo(ArtifactShareKind.MATERIAL)
                .and(TArtifactShare::resourceType.name).isEqualTo(ArtifactShareResourceType.DRIVE_NODE),
            nameCriteria(nameKeyword),
            cursorCriteria(cursor),
        )
        return find(Query(criteria).with(SORT).limit(limit + 1))
    }

    fun listAccessible(
        userId: String,
        orgIds: Collection<String>,
        nameKeyword: String?,
        cursor: ArtifactShareListCursor?,
        limit: Int,
        channel: AccessibleShareChannel? = null,
        featured: Boolean? = null,
    ): List<TArtifactShare> {
        val criteria = combine(
            Criteria.where(TArtifactShare::shareKind.name).isEqualTo(ArtifactShareKind.MATERIAL)
                .and(TArtifactShare::resourceType.name).isEqualTo(ArtifactShareResourceType.DRIVE_NODE),
            accessiblePermissionCriteria(userId, orgIds, channel),
            featuredCriteria(featured),
            nameCriteria(nameKeyword),
            cursorCriteria(cursor),
        )
        return find(Query(criteria).with(SORT).limit(limit + 1))
    }

    private fun accessiblePermissionCriteria(
        userId: String,
        orgIds: Collection<String>,
        channel: AccessibleShareChannel?,
    ): Criteria {
        val orgIdList = orgIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val directed = directedAccessCriteria(userId, orgIdList)
        val publicVisibility = Criteria.where(TArtifactShare::visibility.name).isEqualTo(ShareVisibility.PUBLIC)
        return when (channel) {
            null -> Criteria().orOperator(
                publicVisibility,
                Criteria.where(TArtifactShare::createdBy.name).isEqualTo(userId),
                *directed,
            )
            AccessibleShareChannel.ALL -> Criteria().orOperator(publicVisibility, *directed)
            AccessibleShareChannel.PUBLIC -> publicVisibility
            AccessibleShareChannel.CUSTOM -> Criteria().andOperator(
                Criteria.where(TArtifactShare::visibility.name).isEqualTo(ShareVisibility.CUSTOM),
                Criteria().orOperator(*directed),
            )
        }
    }

    private fun featuredCriteria(featured: Boolean?): Criteria? {
        if (featured != true) {
            return null
        }
        return Criteria.where(TArtifactShare::featured.name).isEqualTo(true)
    }

    private fun directedAccessCriteria(userId: String, orgIdList: List<String>): Array<Criteria> {
        val clauses = mutableListOf(
            Criteria.where(TArtifactShare::userIds.name).isEqualTo(userId),
        )
        if (orgIdList.isNotEmpty()) {
            clauses += Criteria.where(TArtifactShare::orgIds.name).`in`(orgIdList)
        }
        return clauses.toTypedArray()
    }

    private fun nameCriteria(nameKeyword: String?): Criteria? {
        val keyword = nameKeyword?.trim().orEmpty()
        if (keyword.isEmpty()) {
            return null
        }
        return Criteria.where(TArtifactShare::artifactName.name).regex(EscapeUtils.escapeRegex(keyword), "i")
    }

    private fun cursorCriteria(cursor: ArtifactShareListCursor?): Criteria? {
        if (cursor == null) {
            return null
        }
        return Criteria().orOperator(
            Criteria.where(TArtifactShare::lastModifiedDate.name).lt(cursor.lastModifiedDate),
            Criteria.where(TArtifactShare::lastModifiedDate.name).isEqualTo(cursor.lastModifiedDate)
                .and(ID).lt(cursor.id),
        )
    }

    private fun combine(vararg parts: Criteria?): Criteria {
        val criteriaList = parts.filterNotNull()
        return if (criteriaList.size == 1) {
            criteriaList[0]
        } else {
            Criteria().andOperator(*criteriaList.toTypedArray())
        }
    }

    companion object {
        private val SORT = Sort.by(
            Sort.Order.desc(TArtifactShare::lastModifiedDate.name),
            Sort.Order.desc(ID),
        )
    }
}
