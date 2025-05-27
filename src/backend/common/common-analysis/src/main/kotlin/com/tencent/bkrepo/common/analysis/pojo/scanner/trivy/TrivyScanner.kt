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

package com.tencent.bkrepo.common.analysis.pojo.scanner.trivy

import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.artifact.constant.PUBLIC_GLOBAL_PROJECT
import com.tencent.bkrepo.common.artifact.constant.PUBLIC_VULDB_REPO
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "trivy扫描器配置")
class TrivyScanner(
    override val name: String,
    override val version: String,
    @get:Schema(title = "扫描器缓存目录，会在rootPath下创建，存放漏洞数据库文件目录，需要以.开头，否则会被定时任务清理")
    val cacheDir: String = ".cache",
    @get:Schema(title = "使用的容器镜像")
    val container: TrivyDockerImage,
    @get:Schema(title = "漏洞库配置")
    val vulDbConfig: VulDbConfig = VulDbConfig()
) : Scanner(name, TYPE, version) {
    companion object {
        const val TYPE = "trivy"
    }
}

@Schema(title = "漏洞数据库配置")
data class VulDbConfig(
    @get:Schema(title = "从制品库下载使用的projectId，仅dbSource为[DbSource.REPO]时有有效")
    val projectId: String = PUBLIC_GLOBAL_PROJECT,
    @get:Schema(title = "从制品库下载使用的repo，会从该repo的trivy目录中下载最新的文件，仅dbSource为[DbSource.REPO]时有有效")
    val repo: String = PUBLIC_VULDB_REPO,
    @get:Schema(title = "漏洞库来源")
    val dbSource: Int = DbSource.REPO.code,
)

@Schema(title = "Trivy容器镜像配置")
data class TrivyDockerImage(
    @get:Schema(title = "使用的镜像名和版本")
    val image: String,
    @get:Schema(title = "docker仓库用户")
    val dockerRegistryUsername: String?,
    @get:Schema(title = "docker仓库密码")
    val dockerRegistryPassword: String?,
    @get:Schema(title = "容器内的工作目录")
    val workDir: String = "/data",
    @get:Schema(title = "输入目录，相对于workDir的路径")
    val inputDir: String = "/package",
    @get:Schema(title = "输出目录，相对于workDir的路径")
    val outputDir: String = "/output"
)

/**
 * 漏洞库来源
 */
enum class DbSource(val code: Int) {
    /**
     * 从制品库下载
     */
    REPO(0),

    /**
     * 调用Trivy命令下载
     */
    TRIVY(1)
}
