package com.tencent.bkrepo.common.query.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.serializer.RuleDeserializer
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询规则")
@JsonDeserialize(using = RuleDeserializer::class)
sealed class Rule {

    @ApiModel("嵌套查询规则")
    data class NestedRule(
        @ApiModelProperty("规则集")
        val rules: MutableList<Rule>,
        @ApiModelProperty("组合条件类型")
        val relation: RelationType = RelationType.DEFAULT
    ) : Rule() {

        enum class RelationType {
            AND,
            OR,
            NOR;

            companion object {
                val DEFAULT = AND

                fun lookup(value: String): RelationType {
                    val upperCase = value.toUpperCase()
                    return values().find { it.name == upperCase } ?: DEFAULT
                }
            }
        }
    }

    @ApiModel("条件查询规则")
    data class QueryRule(
        @ApiModelProperty("字段")
        val field: String,
        @ApiModelProperty("值")
        val value: Any,
        @ApiModelProperty("操作类型")
        val operation: OperationType = OperationType.DEFAULT
    ) : Rule()

    @ApiModel("固定查询规则")
    data class FixedRule(val wrapperRule: Rule) : Rule()

    fun toFixed(): FixedRule {
        return FixedRule(this)
    }
}
