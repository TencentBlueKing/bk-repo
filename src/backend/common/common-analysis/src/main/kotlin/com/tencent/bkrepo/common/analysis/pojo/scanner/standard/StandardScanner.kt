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

import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.api.constant.CharPool.COLON
import com.tencent.bkrepo.common.operate.api.annotation.Sensitive
import com.tencent.bkrepo.common.operate.api.handler.MaskPartString
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 标准扫描器，后续可逐步替换掉其他扫描器实现，移除其余扫描器代码
 */
@ApiModel("标准扫描器配置")
class StandardScanner(
    override val name: String,
    @ApiModelProperty("扫描器镜像")
    val image: String,
    @ApiModelProperty("docker仓库用户")
    val dockerRegistryUsername: String?,
    @ApiModelProperty("docker仓库密码")
    val dockerRegistryPassword: String?,
    @ApiModelProperty("扫描器容器启动CMD")
    val cmd: String,
    override val version: String = image.substring(image.lastIndexOf(COLON) + 1, image.length),
    @ApiModelProperty("扫描器参数")
    val args: List<Argument> = emptyList(),
) : Scanner(name, TYPE, version) {
    companion object {
        const val TYPE = "standard"
        const val ARG_KEY_PKG_TYPE = "packageType"
        const val ARG_KEY_PKG_KEY = "packageKey"
        const val ARG_KEY_PKG_VERSION = "packageVersion"
        const val ARG_KEY_MAX_TIME = "maxTime"
    }

    @ApiModel("扫描器参数")
    data class Argument(
        @ApiModelProperty("参数类型")
        val type: String,
        @ApiModelProperty("参数名")
        val key: String,
        @ApiModelProperty("参数值")
        @Sensitive(handler = MaskPartString::class)
        val value: String? = null,
        @ApiModelProperty("描述")
        val des: String = ""
    ) {
        companion object {
            fun string(key: String, value: String = "", desc: String? = ""): Argument {
                return Argument(ArgumentType.STRING.name, key, value, desc.orEmpty())
            }

            fun number(key: String, value: Number, desc: String? = ""): Argument {
                return Argument(ArgumentType.NUMBER.name, key, value.toString(), desc.orEmpty())
            }
        }
    }

    /**
     * 参数类型
     */
    enum class ArgumentType { NUMBER, STRING, BOOLEAN; }
}
