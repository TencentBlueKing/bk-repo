/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.preview.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("预览文件信息")
data class FileAttribute (
    // 存储类型：0-bkrepo文件，1-远程文件
    var storageType: Int = 0,

    // 项目id，bkrepo文件需要
    var projectId: String? = null,

    // 仓库名称，bkrepo文件需要
    var repoName: String? = null,

    // 文件全路径，bkrepo文件需要
    var artifactUri: String? = null,

    // 文件名称
    var fileName: String? = null,

    // 文件的完整下载地址，远程文件需要
    var url: String? = null,

    // 文件的md5
    val md5: String? = null,

    // baseUrl
    var baseUrl: String? = null,

    // office预览类型（image / pdf)
    val officePreviewType: String = "pdf",

    // 转换后的文件名
    var outFilePath: String? = null,
    val originFilePath: String? = null,
    val cacheListName: String? = null,
    var convertFileName: String? = null,

    // xlsx是否转成html
    var isHtmlView: Boolean = false,

    // 文件类型
    var type: FileType? = null,

    // 文件后缀
    var suffix: String? = null,
)