/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.analysis.pojo.scanner

object LicenseOverviewKey {
    fun overviewKeyOf(level: String): String {
        if (isRiskLevel(level)) {
            return overviewKeyOfLicenseRisk(level)
        }
        return "license${level.capitalize()}Count"
    }

    private fun overviewKeyOfLicenseRisk(riskLevel: String?): String {
        val level = if (riskLevel.isNullOrEmpty() || riskLevel == LEVEL_NA) {
            LEVEL_NOT_AVAILABLE
        } else {
            riskLevel
        }
        return "licenseRisk${level.capitalize()}Count"
    }

    private fun isRiskLevel(level: String): Boolean {
        val upperCaseLevel = level.toUpperCase()
        return Level.values().firstOrNull { it.name == upperCaseLevel } != null
            || upperCaseLevel == LEVEL_NA
            || upperCaseLevel == LEVEL_NOT_AVAILABLE.toUpperCase()
    }

    /**
     * 扫描器尚未支持的证书类型数量KEY
     */
    private const val LEVEL_NOT_AVAILABLE = "notAvailable"
    private const val LEVEL_NA = "N/A"

    /**
     * 报告许可证总数
     */
    const val TOTAL = "total"

    /**
     * 无风险许可证数量
     */
    const val NIL = "nil"
}
