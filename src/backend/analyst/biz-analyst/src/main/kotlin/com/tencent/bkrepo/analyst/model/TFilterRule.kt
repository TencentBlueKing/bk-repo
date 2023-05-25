/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.analyst.model

import com.tencent.bkrepo.analyst.pojo.Constant.FILTER_RULE_TYPE_IGNORE
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 制品分析结果忽略规则
 */
@Document("filter_rule")
@CompoundIndexes(
    CompoundIndex(name = "projectId_name_idx", def = "{'projectId': 1, 'name': 1}", unique = true, background = true)
)
data class TFilterRule(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    /**
     * 规则名
     */
    val name: String,
    /**
     * 规则描述
     */
    val description: String,

    /**
     * 目标项目，系统级的规则为空字符串
     */
    val projectId: String,

    /**
     * 需要应用规则的项目，为空表示全部应用，该字段仅对系统级规则有效
     */
    val projectIds: List<String>? = null,

    /**
     * 目标仓库名
     */
    val repoName: String? = null,

    /**
     * 目标扫描方案ID
     */
    val planId: String? = null,

    /**
     * 目标路径
     */
    val fullPath: String? = null,

    /**
     * 目标包名
     */
    val packageKey: String? = null,

    /**
     * 目标版本
     */
    val packageVersion: String? = null,

    /**
     * 存在风险的包名
     */
    val riskyPackageKeys: Set<String>? = null,

    /**
     * 存在风险的包和版本，key为存在风险的包名，value为存在风险的包版本范围
     *
     * 例如，key: spring-messaging, value: <4.3.16,>=4.4,<5.0.5
     */
    val riskyPackageVersions: Map<String, String>? = null,

    /**
     * 需要忽略的漏洞
     */
    val vulIds: Set<String>? = null,

    /**
     * 小于该等级的漏洞将被忽略
     */
    val severity: Int? = null,

    /**
     * 需要忽略的许可证
     */
    val licenseNames: Set<String>? = null,

    /**
     * 规则类型
     */
    val type: Int = FILTER_RULE_TYPE_IGNORE
)
