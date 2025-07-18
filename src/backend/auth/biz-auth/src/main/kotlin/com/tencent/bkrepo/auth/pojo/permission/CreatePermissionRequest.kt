/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.pojo.permission

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "创建权限请求")
data class CreatePermissionRequest(
    @get:Schema(title = "资源类型")
    val resourceType: ResourceType = ResourceType.REPO,
    @get:Schema(title = "项目ID")
    val projectId: String? = null,
    @get:Schema(title = "权限名")
    val permName: String,
    @get:Schema(title = "关联仓库名")
    val repos: List<String> = emptyList(),
    @get:Schema(title = "匹配路径")
    val includePattern: List<String> = emptyList(),
    @get:Schema(title = "不匹配路径")
    val excludePattern: List<String> = emptyList(),
    @get:Schema(title = "绑定用户")
    val users: List<String> = emptyList(),
    @get:Schema(title = "绑定角色")
    val roles: List<String> = emptyList(),
    @get:Schema(title = "绑定部门")
    val departments: List<String> = emptyList(),
    @get:Schema(title = "权限动作")
    val actions: List<PermissionAction> = emptyList(),
    @get:Schema(title = "创建人")
    val createBy: String,
    @get:Schema(title = "修改人")
    val updatedBy: String
)
