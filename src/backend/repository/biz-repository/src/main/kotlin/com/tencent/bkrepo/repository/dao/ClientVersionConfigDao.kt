package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TClientVersionConfig
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientVersionConfigListOption
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
class ClientVersionConfigDao : SimpleMongoDao<TClientVersionConfig>() {

    fun findByKey(
        productId: String,
        platform: String,
        arch: String,
        targetUserId: String?,
    ): TClientVersionConfig? {
        return findOne(Query.query(keyCriteria(productId, platform, arch, targetUserId)))
    }

    fun existsEnabledUserConfigByScope(
        productId: String,
        platform: String,
        arch: String,
    ): Boolean {
        val base = Criteria.where(TClientVersionConfig::productId.name).`is`(productId)
            .and(TClientVersionConfig::platform.name).`is`(platform)
            .and(TClientVersionConfig::arch.name).`is`(arch)
            .and(TClientVersionConfig::enabled.name).`is`(true)
            .and(TClientVersionConfig::targetUserId.name).ne(null)
        return exists(Query.query(base))
    }

    fun forEachInBatches(
        batchSize: Int,
        action: (TClientVersionConfig) -> Unit,
    ) {
        var skip = 0L
        while (true) {
            val batch = find(Query().skip(skip).limit(batchSize))
            if (batch.isEmpty()) break
            batch.forEach(action)
            if (batch.size < batchSize) break
            skip += batchSize
        }
    }

    fun pageByProductId(productId: String?, pageNumber: Int, pageSize: Int): Page<TClientVersionConfig> {
        val query = buildProductIdQuery(productId)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = count(query)
        val records = find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    fun pageByQuery(option: ClientVersionConfigListOption): Page<TClientVersionConfig> {
        val query = buildListQuery(option)
        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)
        val totalRecords = count(query)
        val records = find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    private fun buildListQuery(option: ClientVersionConfigListOption): Query {
        val criteria = Criteria()
        normalize(option.productId)?.let {
            criteria.and(TClientVersionConfig::productId.name).`is`(it)
        }
        normalize(option.platform)?.let {
            criteria.and(TClientVersionConfig::platform.name).`is`(it)
        }
        val archList = option.archs
            ?.mapNotNull { normalize(it) }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
        when {
            normalize(option.arch) != null -> {
                criteria.and(TClientVersionConfig::arch.name).`is`(normalize(option.arch))
            }
            archList != null -> {
                criteria.and(TClientVersionConfig::arch.name).`in`(archList)
            }
        }
        val targetUserId = trimValue(option.targetUserId)
        if (targetUserId != null) {
            criteria.and(TClientVersionConfig::targetUserId.name).`is`(targetUserId)
        } else {
            when (option.scope?.trim()?.lowercase()) {
                SCOPE_GLOBAL -> criteria.and(TClientVersionConfig::targetUserId.name).`is`(null)
                SCOPE_USER -> criteria.and(TClientVersionConfig::targetUserId.name).ne(null)
            }
        }
        option.enabled?.let {
            criteria.and(TClientVersionConfig::enabled.name).`is`(it)
        }
        trimValue(option.latestVersion)?.let {
            criteria.and(TClientVersionConfig::latestVersion.name).`is`(it)
        }
        val query = if (criteria.criteriaObject.isEmpty()) {
            Query()
        } else {
            Query(criteria)
        }
        query.with(resolveSort(option.sortField, option.sortDirection))
        return query
    }

    private fun resolveSort(sortField: String?, sortDirection: String?): Sort {
        val direction = if (sortDirection?.trim()?.uppercase() == "ASC") {
            Sort.Direction.ASC
        } else {
            Sort.Direction.DESC
        }
        val field = when (sortField?.trim()) {
            TClientVersionConfig::productId.name -> TClientVersionConfig::productId.name
            TClientVersionConfig::platform.name -> TClientVersionConfig::platform.name
            TClientVersionConfig::arch.name -> TClientVersionConfig::arch.name
            TClientVersionConfig::latestVersion.name -> TClientVersionConfig::latestVersion.name
            TClientVersionConfig::targetUserId.name -> TClientVersionConfig::targetUserId.name
            TClientVersionConfig::createdDate.name -> TClientVersionConfig::createdDate.name
            TClientVersionConfig::enabled.name -> TClientVersionConfig::enabled.name
            else -> TClientVersionConfig::lastModifiedDate.name
        }
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "_id"))
    }

    private fun buildProductIdQuery(productId: String?): Query {
        val query = if (productId.isNullOrBlank()) {
            Query()
        } else {
            Query.query(Criteria.where(TClientVersionConfig::productId.name).`is`(productId))
        }
        query.with(
            Sort.by(
                Sort.Order.desc(TClientVersionConfig::lastModifiedDate.name),
                Sort.Order.desc("_id"),
            ),
        )
        return query
    }

    private fun nullTargetUserCriteria(): Criteria {
        return Criteria.where(TClientVersionConfig::targetUserId.name).`is`(null)
    }

    private fun keyCriteria(
        productId: String,
        platform: String,
        arch: String,
        targetUserId: String?,
    ): Criteria {
        val base = Criteria.where(TClientVersionConfig::productId.name).`is`(productId)
            .and(TClientVersionConfig::platform.name).`is`(platform)
            .and(TClientVersionConfig::arch.name).`is`(arch)
        val scopeKey = if (targetUserId == null) {
            nullTargetUserCriteria()
        } else {
            Criteria.where(TClientVersionConfig::targetUserId.name).`is`(targetUserId)
        }
        return Criteria().andOperator(base, scopeKey)
    }

    private fun normalize(value: String?): String? {
        return value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    private fun trimValue(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val SCOPE_GLOBAL = "global"
        private const val SCOPE_USER = "user"
    }
}
