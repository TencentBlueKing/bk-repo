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

package com.tencent.bkrepo.common.analysis.pojo.scanner.scanCodeCheck.scanner

import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("scancode_toolkit(licenses扫描)扫描器配置")
class ScancodeToolkitScanner(
    override val name: String,
    @ApiModelProperty("扫描器版本")
    override val version: String,
    @ApiModelProperty("使用的容器镜像")
    val container: ScancodeToolkitDockerImage
) : Scanner(name, TYPE, version) {
    companion object {
        const val TYPE = "scancodeToolkit"
    }

    @ApiModel("容器镜像配置")
    data class ScancodeToolkitDockerImage(
        @ApiModelProperty("使用的镜像名和版本")
        val image: String,
        @ApiModelProperty("docker仓库用户")
        val dockerRegistryUsername: String?,
        @ApiModelProperty("docker仓库密码")
        val dockerRegistryPassword: String?,
        @ApiModelProperty("容器内的工作目录")
        val workDir: String = "/data",
        @ApiModelProperty("输入目录，相对于workDir的路径")
        val inputDir: String = "/package",
        @ApiModelProperty("输出目录，相对于workDir的路径")
        val outputDir: String = "/output"
    )
}
