/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.pojo.repo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.metadata.constant.SYSTEM_USER
import io.swagger.v3.oas.annotations.media.Schema


/**
 * 创建仓库请求
 */
@Schema(title = "创建仓库请求")
data class RepoCreateRequest(
    @get:Schema(title = "所属项目id", required = true)
    override val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    override val name: String,
    @get:Schema(title = "仓库类型", required = true)
    val type: RepositoryType,
    @get:Schema(title = "仓库类别", required = true)
    val category: RepositoryCategory,
    @get:Schema(title = "是否公开", required = true)
    val public: Boolean,
    @get:Schema(title = "简要描述", required = false)
    val description: String? = null,
    @get:Schema(title = "仓库配置", required = true)
    val configuration: RepositoryConfiguration? = null,
    @get:Schema(title = "存储凭证key", required = false)
    val storageCredentialsKey: String? = null,
    @get:Schema(title = "仓库配额", required = false)
    val quota: Long? = null,
    @get:Schema(title = "来自插件的请求", required = false)
    val pluginRequest: Boolean = false,

    @get:Schema(title = "操作用户", required = false)
    val operator: String = SYSTEM_USER,
    @get:Schema(title = "是否展示", required = true)
    val display: Boolean = true,
) : RepoRequest
