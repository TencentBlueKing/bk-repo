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

package com.tencent.bkrepo.repository.pojo.node.user

import io.swagger.v3.oas.annotations.media.Schema


/**
 * 用户节点移动/复制请求
 */
@Schema(title = "用户节点移动/复制请求")
data class UserNodeMoveCopyRequest(
    @get:Schema(title = "源项目id", required = true)
    val srcProjectId: String,
    @get:Schema(title = "源仓库名称", required = true)
    val srcRepoName: String,
    @get:Schema(title = "源节点路径", required = true)
    val srcFullPath: String,
    @get:Schema(title = "目的项目id", required = false)
    val destProjectId: String? = null,
    @get:Schema(title = "目的仓库名称", required = false)
    val destRepoName: String? = null,
    @get:Schema(title = "目的路径", required = true)
    val destFullPath: String,
    @Deprecated("This property is deprecated!", ReplaceWith("destFullPath"))
    @get:Schema(title = "目的路径", required = false)
    val destPath: String? = null,
    @get:Schema(title = "同名文件是否覆盖", required = false)
    val overwrite: Boolean = false
)
