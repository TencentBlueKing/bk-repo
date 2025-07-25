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

package com.tencent.bkrepo.repository.pojo.node

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "节点列表选项")
data class NodeListOption(
    @get:Schema(title = "当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @get:Schema(title = "分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @get:Schema(title = "是否包含目录")
    val includeFolder: Boolean = true,
    @get:Schema(title = "是否包含元数据")
    val includeMetadata: Boolean = false,
    @get:Schema(title = "是否深度查询文件")
    val deep: Boolean = false,
    @get:Schema(title = "是否排序，目录在前，文件在后，并按照文件名称排序")
    val sort: Boolean = false,
    @get:Schema(title = "排序字段")
    val sortProperty: List<String> = emptyList(),
    @get:Schema(title = "排序方向")
    val direction: List<String> = emptyList(),
    @get:Schema(title = "无权限路径")
    var noPermissionPath: List<String> = emptyList(),
    @get:Schema(title = "有权限的路径")
    var hasPermissionPath: List<String>? = null,
    @get:Schema(title = "操作用户")
    var operator: String = SYSTEM_USER,
)
