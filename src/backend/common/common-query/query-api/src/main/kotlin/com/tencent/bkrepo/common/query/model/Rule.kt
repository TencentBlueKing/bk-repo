/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.query.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.serializer.RuleDeserializer
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "查询规则")
@JsonDeserialize(using = RuleDeserializer::class)
sealed class Rule {

    @Schema(title = "嵌套查询规则")
    data class NestedRule(
        @get:Schema(title = "规则集")
        val rules: MutableList<Rule>,
        @get:Schema(title = "组合条件类型")
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

    @Schema(title = "条件查询规则")
    data class QueryRule(
        @get:Schema(title = "字段")
        val field: String,
        @get:Schema(title = "值")
        val value: Any,
        @get:Schema(title = "操作类型")
        val operation: OperationType = OperationType.DEFAULT
    ) : Rule() {
        fun toFixed(): FixedRule {
            return FixedRule(this)
        }
    }

    @Schema(title = "固定查询规则")
    data class FixedRule(val wrapperRule: QueryRule) : Rule()
}
