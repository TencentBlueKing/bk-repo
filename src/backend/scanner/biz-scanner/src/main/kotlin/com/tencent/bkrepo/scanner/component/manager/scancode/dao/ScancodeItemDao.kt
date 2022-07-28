package com.tencent.bkrepo.scanner.component.manager.scancode.dao

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeItem
import com.tencent.bkrepo.scanner.component.manager.ResultItemDao
import com.tencent.bkrepo.scanner.component.manager.scancode.model.TScancodeItem
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ScancodeToolkitResultArguments
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.stereotype.Repository

@Repository
class ScancodeItemDao : ResultItemDao<TScancodeItem>() {
    fun customizePageBy(criteria: Criteria, arguments: ScancodeToolkitResultArguments): Criteria {
        if (!arguments.riskLevels.isNullOrEmpty()) {
            criteria.and(dataKey(ScancodeItem::riskLevel.name)).inValues(arguments.riskLevels!!)
        }
        if (!arguments.licenseIds.isNullOrEmpty()) {
            criteria.and(dataKey(ScancodeItem::licenseId.name)).inValues(arguments.licenseIds!!)
        }
        logger.info("ScancodeItemDao customizePageBy criteria:${criteria.toJsonString()}")
        return criteria
    }

    private fun dataKey(name: String) = "${TScancodeItem::data.name}.$name"

    fun pageOf(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        pageLimit: PageLimit,
        arguments: ScancodeToolkitResultArguments
    ): Page<TScancodeItem> {
        val criteria = buildCriteria(credentialsKey, sha256, scanner)
        customizePageBy(criteria, arguments)
        val query = Query(criteria).with(PageRequest.of(pageLimit.pageNumber - 1, pageLimit.pageSize))
        val total = count(Query.of(query).limit(0).skip(0))
        val data = find(query)
        return Page(pageLimit.pageNumber, pageLimit.pageSize, total, data)
    }
}
