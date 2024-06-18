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

package com.tencent.bkrepo.common.analysis.pojo.scanner

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.scanner.DependencyScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanner
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import org.slf4j.LoggerFactory
import kotlin.math.max

@ApiModel("扫描器配置")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ArrowheadScanner::class, name = ArrowheadScanner.TYPE),
    JsonSubTypes.Type(value = DependencyScanner::class, name = DependencyScanner.TYPE),
    JsonSubTypes.Type(value = TrivyScanner::class, name = TrivyScanner.TYPE),
    JsonSubTypes.Type(value = ScancodeToolkitScanner::class, name = ScancodeToolkitScanner.TYPE),
    JsonSubTypes.Type(value = StandardScanner::class, name = StandardScanner.TYPE)
)
open class Scanner(
    @ApiModelProperty("扫描器名")
    open val name: String,
    @ApiModelProperty("扫描器类型")
    val type: String,
    @ApiModelProperty("扫描器版本")
    open val version: String,
    @ApiModelProperty("扫描器描述信息")
    val description: String = "",
    @ApiModelProperty("扫描器根目录")
    val rootPath: String = type,
    @ApiModelProperty("扫描结束后是否清理工作目录")
    val cleanWorkDir: Boolean = true,
    @ApiModelProperty("最大允许的1MB文件扫描时间")
    val maxScanDurationPerMb: Long = DEFAULT_MAX_SCAN_DURATION,
    @ApiModelProperty("支持扫描的文件类型")
    val supportFileNameExt: List<String> = DEFAULT_SUPPORT_FILE_NAME_EXTENSION,
    @ApiModelProperty("支持扫描的包类型")
    val supportPackageTypes: List<String> = emptyList(),
    @ApiModelProperty("支持扫描的类型")
    val supportScanTypes: List<String> = emptyList(),
    @ApiModelProperty("支持的分发器")
    val supportDispatchers: List<String> = emptyList(),
    @ApiModelProperty("执行扫描所需要的内存大小")
    @Deprecated("使用limitMem替代")
    val memory: Long = DEFAULT_MEM,
    @ApiModelProperty("容器limit mem")
    val limitMem: Long = 32 * GB,
    @ApiModelProperty("容器 request mem")
    val requestMem: Long = 16 * GB,
    @ApiModelProperty("容器request ephemeralStorage")
    val requestStorage: Long = 16 * GB,
    @ApiModelProperty("容器limit ephemeralStorage")
    val limitStorage: Long = 128 * GB,
    @ApiModelProperty("容器request cpu")
    val requestCpu: Double = 4.0,
    @ApiModelProperty("容器limit cpu")
    val limitCpu: Double = 16.0
) {
    /**
     * 获取待扫描文件最大允许扫描时长
     *
     * @param size 待扫描文件大小
     */
    open fun maxScanDuration(size: Long): Long {
        val sizeMib = size / 1024L / 1024L
        if (sizeMib == 0L) {
            return DEFAULT_MIN_SCAN_DURATION
        }
        val maxScanDuration = if (Long.MAX_VALUE / sizeMib > maxScanDurationPerMb) {
            maxScanDurationPerMb * sizeMib
        } else {
            logger.warn("file too large size[$size]")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
        }
        return max(DEFAULT_MIN_SCAN_DURATION, maxScanDuration)
    }

    companion object {
        val DEFAULT_SUPPORT_FILE_NAME_EXTENSION = listOf(
            "apk", "apks", "aab", "exe", "so", "ipa", "dmg", "jar", "gz", "tar", "zip"
        )
        private val logger = LoggerFactory.getLogger(Scanner::class.java)
        private const val DEFAULT_MAX_SCAN_DURATION = 6 * 1000L

        /**
         * 默认至少允许扫描的时间
         */
        private const val DEFAULT_MIN_SCAN_DURATION = 3 * 60L * 1000L

        /**
         * 默认内存大小
         */
        private const val DEFAULT_MEM = 32L * 1024L * 1024L * 1024L
        const val GB = 1024 * 1024 * 1024L
    }
}
