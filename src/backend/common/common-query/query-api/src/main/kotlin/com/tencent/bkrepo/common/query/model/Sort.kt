package com.tencent.bkrepo.common.query.model

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 排序规则
 */
@ApiModel("排序规则")
data class Sort(
    @ApiModelProperty("排序字段")
    val properties: List<String>,
    @ApiModelProperty("排序方式")
    val direction: Direction = Direction.DEFAULT
) {
    enum class Direction {
        ASC,
        DESC;

        companion object {
            val DEFAULT = ASC

            fun lookup(value: String): Direction {
                val upperCase = value.toUpperCase()
                return values().find { it.name == upperCase } ?: DEFAULT
            }
        }
    }
}
