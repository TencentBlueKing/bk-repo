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

@ApiModel("预览属性")
data class PreviewOptions(
    @ApiModelProperty("是否禁止演示模式")
    var pdfPresentationModeDisable: Boolean = true,
    @ApiModelProperty("是否禁止打开文件")
    var pdfOpenFileDisable: Boolean = true,
    @ApiModelProperty("是否禁止打印转换生成的PDF文件")
    var pdfPrintDisable: Boolean = true,
    @ApiModelProperty("是否禁止下载转换生成的PDF文件")
    var pdfDownloadDisable: Boolean = true,
    @ApiModelProperty("是否禁止bookmarkFileConvertQueueTask")
    var pdfBookmarkDisable: Boolean = true,
    @ApiModelProperty("是否禁止签名")
    var pdfDisableEditing: Boolean = true,
    @ApiModelProperty("是否关闭 office 预览切换开关")
    var switchDisabled: Boolean = true,
)