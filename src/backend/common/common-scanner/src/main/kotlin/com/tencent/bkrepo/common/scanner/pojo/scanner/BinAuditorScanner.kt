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

package com.tencent.bkrepo.common.scanner.pojo.scanner

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("BinAuditor扫描器配置")
class BinAuditorScanner(
    override val name: String,
    /**
     * 扫描器版本，格式为BinAuditorVersion::NvToolsVersion
     */
    override val version: String,
    @ApiModelProperty("扫描器根目录")
    val rootPath: String,
    @ApiModelProperty("扫描器配置文件路径，相对于工作目录")
    val configFilePath: String = DEFAULT_CONFIG_FILE_PATH,
    @ApiModelProperty("扫描结束后是否清理工作目录")
    val cleanWorkDir: Boolean = true,
    @ApiModelProperty("漏洞库配置")
    val nvTools: NvTools,
    @ApiModelProperty("使用的容器镜像")
    val container: BinAuditorDockerImage
) : Scanner(name, TYPE, version) {
    companion object {
        /**
         * 扫描器和漏洞库版本号分隔符
         */
        const val VERSION_SPLIT = "::"
        const val TYPE = "BinAuditor"
        const val DEFAULT_CONFIG_FILE_PATH = "/standalone.toml"
    }
}

@ApiModel("BinAuditor容器镜像配置")
data class BinAuditorDockerImage(
    @ApiModelProperty("使用的镜像名和版本")
    val image: String,
    @ApiModelProperty("容器启动参数")
    val args: String = "",
    @ApiModelProperty("容器内的工作目录")
    val workDir: String = "/data",
    @ApiModelProperty("输入目录，相对于workDir的路径")
    val inputDir: String = "/package",
    @ApiModelProperty("输出目录，相对于workDir的路径")
    val outputDir: String = "/output"
)

/**
 * BinAuditor漏洞库配置
 */
data class NvTools(
    val enabled: Boolean = false,
    val username: String? = null,
    val key: String? = null,
    val host: String? = null
)
