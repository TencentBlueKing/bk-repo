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

package com.tencent.bkrepo.common.analysis.pojo.scanner.standard

import com.fasterxml.jackson.annotation.JsonInclude
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner.Argument
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner.ArgumentType.STRING

/**
 * 分析工具输入
 *
 * 分析工具有两种形式的输入参数
 * 1. --input /path/to/input.json指定输入文件参数，input.json数据格式与本类相同
 * 2. 分析工具输入参数为--token xxx --taskId xxx --bkrepoBaseUrl xxx时调用分析管理服务接口返回的数据格式与本类相同
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolInput(
    /**
     * 子任务id
     */
    val taskId: String,
    /**
     * 工具配置
     */
    val toolConfig: ToolConfig,
    /**
     * 待分析文件路径
     * fileUrls与filePath只有一个字段非null
     * packageType为DOCKER时指向的是镜像tar包路径
     */
    val filePath: String? = null,
    /**
     * 待分析文件sha256，仅filePath字段非null时存在
     */
    val sha256: String? = null,
    /**
     * 仅当从分析管理服务拉取input.json时存在
     * 存在多个url时候第一个为manifest，工具可根据manifest组装成tar，或直接扫描layer
     */
    val fileUrls: List<FileUrl>? = null
) {
    companion object {
        fun create(
            taskId: String,
            fileUrls: List<FileUrl>,
            args: List<Argument>
        ): ToolInput {
            return ToolInput(taskId = taskId, toolConfig = ToolConfig(args), fileUrls = fileUrls)
        }

        fun create(
            taskId: String,
            filePath: String,
            sha256: String,
            args: List<Argument>
        ): ToolInput {
            return ToolInput(taskId = taskId, toolConfig = ToolConfig(args), filePath = filePath, sha256 = sha256)
        }

        fun generateArgs(
            scanner: StandardScanner,
            packageType: String,
            packageSize: Long,
            packageKey: String? = null,
            packageVersion: String? = null,
            extra: Map<String, Any?>?
        ): List<Argument> {
            val args = scanner.args.toMutableList()
            args.add(Argument(STRING.name, StandardScanner.ARG_KEY_PKG_TYPE, packageType))
            val maxTime = scanner.maxScanDuration(packageSize).toString()
            args.add(Argument(StandardScanner.ArgumentType.NUMBER.name, StandardScanner.ARG_KEY_MAX_TIME, maxTime))
            packageKey?.let {
                args.add(Argument(StandardScanner.ArgumentType.STRING.name, StandardScanner.ARG_KEY_PKG_KEY, it))
            }
            packageVersion?.let {
                args.add(Argument(StandardScanner.ArgumentType.STRING.name, StandardScanner.ARG_KEY_PKG_VERSION, it))
            }
            extra?.let { addExtra(args, extra) }
            return args
        }

        private fun addExtra(args: MutableList<Argument>, extra: Map<String, Any?>) {
            extra.forEach { (key, value) ->
                val arg = when (value) {
                    is String -> Argument.string(key, value)
                    is Number -> Argument.number(key, value)
                    else -> Argument.string(key, value.toString())
                }
                args.add(arg)
            }
        }
    }
}

data class FileUrl(val url: String, val name: String, val sha256: String, val size: Long)
data class ToolConfig(val args: List<Argument>)
