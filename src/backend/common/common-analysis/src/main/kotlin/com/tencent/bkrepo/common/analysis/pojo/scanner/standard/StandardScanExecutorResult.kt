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

package com.tencent.bkrepo.common.analysis.pojo.scanner.standard

import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.normalizedLevel
import io.swagger.v3.oas.annotations.media.Schema


data class StandardScanExecutorResult(
    @get:Schema(title = "工具分析结果")
    val output: ToolOutput? = null,
    override val scanStatus: String = output?.status ?: SubScanTaskStatus.FAILED.name
) : ScanExecutorResult(scanStatus, StandardScanner.TYPE) {
    override fun normalizeResult() {
        if (output?.result == null) {
            return
        }

        // 根据（漏洞id-组件id）进行去重
        val securityResults = HashMap<String, SecurityResult>()
        val versionPathsMap = HashMap<String, VersionPaths>()
        output.result?.securityResults?.forEach { securityResult ->
            val result = securityResults.getOrPut("${securityResult.pkgName}-${securityResult.vulId}") {
                securityResult.copy(severity = normalizedLevel(securityResult.severity))
            }
            // 添加组件版本与路径映射关系
            securityResult.versionsPaths.forEach {
                result.versionsPaths.add(updateVersionPaths(securityResult.pkgName, it, versionPathsMap))
            }
            result.pkgVersions.addAll(securityResult.pkgVersions)
        }

        // 去重license
        val licenseResults = HashMap<String, LicenseResult>()
        output.result?.licenseResults?.forEach { licenseResult ->
            val pkgName = licenseResult.pkgName
            val result = licenseResults.getOrPut("${pkgName}-${licenseResult.licenseName}") { licenseResult }
            // 添加组件版本与路径映射关系
            licenseResult.versionsPaths.forEach {
                result.versionsPaths.add(updateVersionPaths(pkgName, it, versionPathsMap))
            }
            result.pkgVersions.addAll(licenseResult.pkgVersions)
        }

        // 替换为去重后的结果
        output.result?.let {
            output.result = it.copy(
                securityResults = securityResults.values.toList(),
                licenseResults = licenseResults.values.toList()
            )
        }
    }

    private fun updateVersionPaths(
        pkgName: String?,
        rawVersionPaths: VersionPaths,
        versionPathsMap: MutableMap<String, VersionPaths>
    ): VersionPaths {
        val version = rawVersionPaths.version
        val versionPaths = versionPathsMap.getOrPut("${pkgName}-${version}") { VersionPaths(version) }
        versionPaths.paths.addAll(rawVersionPaths.paths)
        return versionPaths
    }
}
