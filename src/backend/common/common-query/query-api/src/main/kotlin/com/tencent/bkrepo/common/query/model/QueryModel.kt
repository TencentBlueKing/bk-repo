package com.tencent.bkrepo.common.query.model

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 自定义查询模型
 */
@ApiModel("自定义查询模型")
data class QueryModel(
    val page: PageLimit = PageLimit(),
    val sort: Sort?,
    @ApiModelProperty("筛选字段列表")
    val select: MutableList<String>?,
    var rule: Rule
) {
    fun addQueryRule(newRule: Rule.QueryRule) {
        if (this.rule is Rule.QueryRule) {
            this.rule = Rule.NestedRule(mutableListOf(this.rule, newRule), Rule.NestedRule.RelationType.AND)
        } else {
            val relation = (this.rule as Rule.NestedRule).relation
            val rules = (this.rule as Rule.NestedRule).rules

            if (relation == Rule.NestedRule.RelationType.AND) {
                rules.add(newRule)
            } else {
                this.rule = Rule.NestedRule(mutableListOf(this.rule, newRule), Rule.NestedRule.RelationType.AND)
            }
        }
    }
}
