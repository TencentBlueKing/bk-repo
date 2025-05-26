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

package com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead

import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.api.annotation.Sensitive
import com.tencent.bkrepo.common.api.handler.MaskPartString
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "Arrowhead扫描器配置")
class ArrowheadScanner(
    override val name: String,
    /**
     * 格式为ArrowheadImageVersion::KnowledgeBaseVervion::StandaloneConfigTemplateVersion
     * 或者ArrowheadImageVersion::KnowledgeBaseVervion
     */
    @get:Schema(title = "扫描器版本")
    override val version: String,
    @get:Schema(title = "扫描器配置文件路径，相对于工作目录")
    val configFilePath: String = DEFAULT_CONFIG_FILE_PATH,
    @get:Schema(title = "漏洞知识库配置")
    val knowledgeBase: KnowledgeBase,
    @get:Schema(title = "使用的容器镜像")
    val container: ArrowheadDockerImage
) : Scanner(name, TYPE, version) {
    companion object {
        /**
         * 扫描器和漏洞库版本号分隔符
         */
        const val VERSION_SPLIT = "::"
        const val TYPE = "arrowhead"
        const val DEFAULT_CONFIG_FILE_PATH = "/standalone.toml"
    }
}

@Schema(title = "arrowhead容器镜像配置")
data class ArrowheadDockerImage(
    @get:Schema(title = "使用的镜像名和版本")
    val image: String,
    @get:Schema(title = "docker仓库用户")
    val dockerRegistryUsername: String?,
    @get:Schema(title = "docker仓库密码")
    val dockerRegistryPassword: String?,
    @get:Schema(title = "容器启动参数")
    val args: String = "/data/standalone.toml",
    @get:Schema(title = "容器内的工作目录")
    val workDir: String = "/data",
    @get:Schema(title = "输入目录，相对于workDir的路径")
    val inputDir: String = "/package",
    @get:Schema(title = "输出目录，相对于workDir的路径")
    val outputDir: String = "/output"
)

@Schema(title = "v2 arrowhead漏洞知识库配置")
data class KnowledgeBase(
    @get:Schema(title = "漏洞知识库地址，例如http://127.0.0.1:1234")
    val endpoint: String,
    @get:Schema(title = "漏洞知识库认证id")
    @Sensitive(handler = MaskPartString::class)
    val secretId: String = "",
    @get:Schema(title = "漏洞知识库认证密钥")
    @Sensitive(handler = MaskPartString::class)
    val secretKey: String = ""
)
