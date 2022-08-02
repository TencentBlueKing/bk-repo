package com.tencent.bkrepo.scanner.component.manager.scancode.dao

import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeItem
import com.tencent.bkrepo.scanner.component.manager.ResultItemDao
import com.tencent.bkrepo.scanner.component.manager.scancode.model.TScancodeItem
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ScancodeToolkitResultArguments
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.stereotype.Repository

@Repository
class ScancodeItemDao : ResultItemDao<TScancodeItem>() {
    override fun customizePageBy(criteria: Criteria, arguments: LoadResultArguments): Criteria {
        require(arguments is ScancodeToolkitResultArguments)
        if (!arguments.riskLevels.isNullOrEmpty()) {
            criteria.and(dataKey(ScancodeItem::riskLevel.name)).inValues(arguments.riskLevels!!)
        }
        if (!arguments.licenseIds.isNullOrEmpty()) {
            criteria.and(dataKey(ScancodeItem::licenseId.name)).inValues(arguments.licenseIds!!)
        }
        return criteria
    }

    private fun dataKey(name: String) = "${TScancodeItem::data.name}.$name"
}
