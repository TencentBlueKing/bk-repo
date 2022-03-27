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
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.normalizedLevel
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.removeRootDirPath
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import org.apache.commons.lang3.StringEscapeUtils

@ApiModel("CVE信息")
data class CveSecItem(
    @ApiModelProperty("文件路径")
    @JsonAlias("path")
    val path: String,

    @ApiModelProperty("组件名")
    @JsonAlias("component")
    var component: String,

    @ApiModelProperty("库版本")
    @JsonAlias("version")
    val version: String,

    @ApiModelProperty("漏洞影响版本")
    @JsonAlias("version_effected")
    val versionEffected: String,

    @ApiModelProperty("漏洞修复版本")
    @JsonAlias("version_fixed")
    val versionFixed: String,

    @ApiModelProperty("漏洞名")
    @JsonAlias("name")
    val name: String,

    @ApiModelProperty("漏洞利用类型")
    @JsonAlias("category")
    val category: String,

    @ApiModelProperty("漏洞类型")
    @JsonAlias("category_type")
    val categoryType: String,

    @ApiModelProperty("漏洞描述")
    @JsonAlias("description")
    val description: String,

    @ApiModelProperty("官方解决方案")
    @JsonAlias("official_solution")
    val officialSolution: String,

    @ApiModelProperty("解决方案")
    @JsonAlias("defense_solution")
    val defenseSolution: String,

    @ApiModelProperty("相关链接")
    @JsonAlias("reference")
    val references: List<String>,

    @ApiModelProperty("poc id")
    @JsonAlias("poc_id")
    val pocId: String,

    @ApiModelProperty("cve id")
    @JsonAlias("cve_id")
    val cveId: String,

    @ApiModelProperty("cnvd id")
    @JsonAlias("cnvd_id")
    val cnvdId: String,

    @ApiModelProperty("cnnvd id")
    @JsonAlias("cnnvd_id")
    val cnnvdId: String,

    @ApiModelProperty("cwe id")
    @JsonAlias("cwe_id")
    val cweId: String,

    /**
     * CRITICAL,HIGH,MEDIUM,LOW
     */
    @ApiModelProperty("cvss等级")
    @JsonAlias("cvss_rank")
    val cvssRank: String,

    @ApiModelProperty("cvss 评分")
    @JsonAlias("cvss")
    val cvss: Double,

    @ApiModelProperty("cvss V3 漏洞影响评价")
    @JsonAlias("cvss_v3_vector")
    val cvssV3Vector: String?,

    @ApiModelProperty("cvss V2 漏洞影响评价")
    @JsonAlias("cvss_v2_vector")
    val cvssV2Vector: String?,

    @ApiModelProperty("dynamic level")
    @JsonAlias("dynamic_level")
    val dynamicLevel: Int,

    /**
     * 严重，高危，中危，低危，
     */
    @ApiModelProperty("nvTools 漏洞评级")
    @JsonAlias("level")
    val level: String?
) {

    companion object {

        const val TYPE = "CVE_SEC_ITEM"

        @Suppress("UNCHECKED_CAST")
        fun parseCveSecItems(cveSecItems: Map<String, Any?>): CveSecItem {
            val nvToolsCveInfo = cveSecItems["nvtools_cveinfo"] as Map<String, Any?>?
            val versionFixed =
                StringEscapeUtils.unescapeJava(get(nvToolsCveInfo, cveSecItems, "version_fixed").toString())
            val cvss = nvToolsCveInfo?.get("cvss_v3_score")
                ?: nvToolsCveInfo?.get("cvss_v2_score")
                ?: cveSecItems["cvss"]
            return CveSecItem(
                path = removeRootDirPath(cveSecItems["path"].toString()),
                component = cveSecItems["lib_name"].toString(),
                version = get(nvToolsCveInfo, cveSecItems, "version").toString(),
                versionEffected = get(nvToolsCveInfo, cveSecItems, "version_effected").toString(),
                versionFixed = versionFixed,
                category = get(nvToolsCveInfo, cveSecItems, "category").toString(),
                categoryType = get(nvToolsCveInfo, cveSecItems, "category_type").toString(),
                description = (nvToolsCveInfo?.get("des") ?: cveSecItems["description"]).toString(),
                officialSolution = get(nvToolsCveInfo, cveSecItems, "official_solution").toString(),
                defenseSolution = get(nvToolsCveInfo, cveSecItems, "defense_solution").toString(),
                references = getReferences(nvToolsCveInfo, cveSecItems),
                name = get(nvToolsCveInfo, cveSecItems, "name").toString(),
                pocId = get(nvToolsCveInfo, cveSecItems, "poc_id").toString(),
                cveId = get(nvToolsCveInfo, cveSecItems, "cve_id").toString(),
                cnvdId = get(nvToolsCveInfo, cveSecItems, "cnvd_id").toString(),
                cnnvdId = get(nvToolsCveInfo, cveSecItems, "cnnvd_id").toString(),
                cweId = get(nvToolsCveInfo, cveSecItems, "cwe_id").toString(),
                cvssRank = normalizedLevel(cveSecItems["cvss_rank"].toString()),
                cvss = cvss.toString().toDouble(),
                cvssV3Vector = nvToolsCveInfo?.get("cvss_v3_vector")?.toString(),
                cvssV2Vector = nvToolsCveInfo?.get("cvss_v2_vector")?.toString(),
                dynamicLevel = get(nvToolsCveInfo, cveSecItems, "dynamic_level").toString().toInt(),
                level = nvToolsCveInfo?.get("level")?.toString()?.let { normalizedLevel(it) }
            )
        }

        private fun get(primary: Map<String, Any?>?, secondary: Map<String, Any?>, key: String): Any? {
            return primary?.get(key) ?: secondary[key]
        }

        @Suppress("UNCHECKED_CAST")
        private fun getReferences(primary: Map<String, Any?>?, secondary: Map<String, Any?>): List<String> {
            return primary?.get("reference")?.toString()?.readJsonString<List<String>>()
                ?: secondary["reference"] as List<String>
                ?: emptyList()
        }
    }

}
