/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class CveSecItem(
    /**
     * 文件路径
     */
    @JsonProperty("path")
    val path: String,
    /**
     * 组件名
     */
    @JsonProperty("component")
    @JsonAlias("lib_name")
    var component: String,
    /**
     * 库版本
     */
    @JsonProperty("version")
    val version: String,
    @JsonProperty("poc_id")
    val pocId: String,
    /**
     * 漏洞名
     */
    @JsonProperty("name")
    val name: String,
    /**
     * 漏洞利用类型
     */
    @JsonProperty("category")
    val category: String,
    /**
     * 漏洞类型
     */
    @JsonProperty("category_type")
    val categoryType: String,
    /**
     * 漏洞影响版本
     */
    @JsonProperty("version_effected")
    val versionEffected: String,
    /**
     * 漏洞修复版本
     */
    @JsonProperty("version_fixed")
    val versionFixed: String,
    /**
     * cvss等级
     */
    @JsonProperty("cvss_rank")
    val cvssRank: String,
    /**
     * cvss 评分
     */
    @JsonProperty("cvss")
    val cvss: Double,
    @JsonProperty("cve_id")
    val cveId: String,
    @JsonProperty("cnvd_id")
    val cnvdId: String,
    @JsonProperty("cnnvd_id")
    val cnnvdId: String,
    @JsonProperty("cwe_id")
    val cweId: String,

    @JsonProperty("dynamic_level")
    val dynamicLevel: Int,

    @JsonProperty("level")
    val level: String,
    @JsonProperty("cvss_v3_score")
    val cvssV3Score: String,
    @JsonProperty("cvss_v3_vector")
    val cvssV3Vector: String,
    @JsonProperty("cvss_v2_score")
    val cvssV2Score: String,
    @JsonProperty("cvss_v2_vector")
    val cvssV2Vector: String,
    @JsonProperty("submit_time")
    val submitTime: String,

    @JsonProperty("cvss_v2")
    val cvssV2: CvssV2?,
    @JsonProperty("cvss_v3")
    val cvssV3: CvssV3?,
    @JsonProperty("nvtools_cveinfo")
    val nvtoolsCveinfo: CveSecItem? = null
)

data class CvssV3(
    @JsonProperty("base_score")
    val baseScore: Double,
    @JsonProperty("confidentiality_impact")
    val confidentialityImpact: String,
    @JsonProperty("integrity_impact")
    val integrityImpact: String,
    @JsonProperty("availability_impact")
    val availabilityImpact: String,
    @JsonProperty("attackVector")
    val attackVector: String,
    @JsonProperty("attackComplexity")
    val attackComplexity: String,
    @JsonProperty("privileges_required")
    val privilegesRequired: String,
    @JsonProperty("user_interaction")
    val userInteraction: String,
    @JsonProperty("scope")
    val scope: String
)

data class CvssV2(
    @JsonProperty("base_score")
    val baseScore: Double,
    @JsonProperty("confidentiality_impact")
    val confidentialityImpact: String,
    @JsonProperty("integrity_impact")
    val integrityImpact: String,
    @JsonProperty("availability_impact")
    val availabilityImpact: String,
    @JsonProperty("attackVector")
    val attackVector: String,
    @JsonProperty("attackComplexity")
    val attackComplexity: String,
    @JsonProperty("authentication")
    val authentication: String
)
