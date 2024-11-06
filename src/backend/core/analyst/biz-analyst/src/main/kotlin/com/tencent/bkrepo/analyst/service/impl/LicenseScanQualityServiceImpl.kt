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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.analyst.service.LicenseScanQualityService
import org.springframework.stereotype.Service

@Service
class LicenseScanQualityServiceImpl : LicenseScanQualityService {
    override fun checkLicenseScanQualityRedLine(
        scanQuality: Map<String, Any>,
        scanResultOverview: Map<String, Number>
    ): Boolean {
        LicenseNature.values().forEach {
            if (checkRedLine(it, scanResultOverview, scanQuality)) {
                return false
            }
        }
        return true
    }

    /**
     * 判断扫描结果中对应的数量是否符合质量规则中的设定
     * 质量规则设定为 true，对应扫描结果为 0 才通过
     */
    private fun checkRedLine(
        overviewKey: LicenseNature,
        overview: Map<String, Number>,
        quality: Map<String, Any>
    ): Boolean {
        if (quality[overviewKey.level] == true) {
            val count = overview[LicenseOverviewKey.overviewKeyOf(overviewKey.natureName)]?.toLong() ?: 0L
            return count != 0L
        }
        return false
    }
}
