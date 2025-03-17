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

package com.tencent.bkrepo.analyst.pojo

import com.fasterxml.jackson.annotation.JsonInclude
import com.tencent.bkrepo.common.query.model.Rule
import io.swagger.v3.oas.annotations.media.Schema


@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "扫描方案")
data class ScanPlan(
    @get:Schema(title = "方案ID")
    var id: String? = null,
    @get:Schema(title = "项目ID")
    var projectId: String? = null,
    @get:Schema(title = "方案名称")
    var name: String? = null,
    @get:Schema(title = "方案类型")
    var type: String? = null,
    @get:Schema(title = "扫描类型")
    var scanTypes: List<String>? = null,
    @get:Schema(title = "使用的扫描器")
    var scanner: String? = null,
    @get:Schema(title = "方案描述")
    var description: String? = null,
    @get:Schema(title = "是否有新制品上传时自动扫描")
    var scanOnNewArtifact: Boolean? = null,
    @get:Schema(title = "自动扫描仓库")
    var repoNames: List<String>? = null,
    @get:Schema(title = "自动扫描规则")
    var rule: Rule? = null,
    @get:Schema(title = "质量规则")
    var scanQuality: Map<String, Any>? = null,
    @get:Schema(title = "创建者")
    var createdBy: String? = null,
    @get:Schema(title = "创建时间")
    var createdDate: String? = null,
    @get:Schema(title = "修改者")
    var lastModifiedBy: String? = null,
    @get:Schema(title = "修改时间")
    var lastModifiedDate: String? = null,
    @get:Schema(title = "是否只读")
    var readOnly: Boolean? = null
)
