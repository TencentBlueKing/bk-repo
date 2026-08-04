package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.model.TClientChangelog
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogListOption
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogStatus
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
class ClientChangelogDao : SimpleMongoDao<TClientChangelog>() {

    fun findByKey(
        productId: String,
        version: String,
    ): TClientChangelog? {
        return findOne(Query.query(keyCriteria(productId, version)))
    }

    /**
     * 客户端只读：查找已发布的指定版本记录。
     */
    fun findPublished(
        productId: String,
        version: String,
    ): TClientChangelog? {
        val criteria = Criteria.where(TClientChangelog::productId.name).`is`(productId)
            .and(TClientChangelog::version.name).`is`(version)
            .and(TClientChangelog::status.name).`is`(ClientChangelogStatus.PUBLISHED.name)
        return findOne(Query.query(criteria))
    }

    /**
     * 客户端只读：分页查询某产品的已发布历史。
     */
    fun pagePublished(
        productId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<TClientChangelog> {
        val criteria = Criteria.where(TClientChangelog::productId.name).`is`(productId)
            .and(TClientChangelog::status.name).`is`(ClientChangelogStatus.PUBLISHED.name)

        val query = Query.query(criteria).with(
            Sort.by(
                Sort.Order.desc(TClientChangelog::releasedAt.name),
                Sort.Order.desc(TClientChangelog::version.name),
            ),
        )
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val total = count(query)
        val records = find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, total, records)
    }

    /**
     * 管理端：按多条件分页查询。
     */
    fun pageByOption(option: ClientChangelogListOption): Page<TClientChangelog> {
        val criteria = Criteria()
        normalizeKey(option.productId)?.let {
            criteria.and(TClientChangelog::productId.name).`is`(it)
        }
        trimValue(option.version)?.let {
            criteria.and(TClientChangelog::version.name).`is`(it)
        }
        option.status?.let {
            criteria.and(TClientChangelog::status.name).`is`(it.name)
        }
        val query = Query.query(criteria).with(resolveSort(option.sortField, option.sortDirection))
        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)
        val total = count(query)
        val records = find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, total, records)
    }

    private fun keyCriteria(
        productId: String,
        version: String,
    ): Criteria {
        return Criteria.where(TClientChangelog::productId.name).`is`(productId)
            .and(TClientChangelog::version.name).`is`(version)
    }

    private fun resolveSort(sortField: String?, sortDirection: String?): Sort {
        val direction = if (sortDirection?.trim()?.uppercase() == "ASC") {
            Sort.Direction.ASC
        } else {
            Sort.Direction.DESC
        }
        val field = when (sortField?.trim()) {
            TClientChangelog::version.name -> TClientChangelog::version.name
            TClientChangelog::status.name -> TClientChangelog::status.name
            TClientChangelog::createdDate.name -> TClientChangelog::createdDate.name
            TClientChangelog::lastModifiedDate.name -> TClientChangelog::lastModifiedDate.name
            else -> TClientChangelog::releasedAt.name
        }
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "_id"))
    }

    private fun normalizeKey(value: String?): String? {
        return value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    private fun trimValue(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
