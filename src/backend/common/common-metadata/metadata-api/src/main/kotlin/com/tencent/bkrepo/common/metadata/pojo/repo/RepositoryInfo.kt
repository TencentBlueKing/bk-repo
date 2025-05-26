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

package com.tencent.bkrepo.common.metadata.pojo.repo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import io.swagger.v3.oas.annotations.media.Schema


/**
 * 仓库信息
 */
@Schema(title = "仓库信息")
data class RepositoryInfo(
    val id: String? = null,
    @get:Schema(title = "所属项目id")
    val projectId: String,
    @get:Schema(title = "仓库名称")
    val name: String,
    @get:Schema(title = "仓库类型")
    val type: RepositoryType,
    @get:Schema(title = "仓库类别")
    val category: RepositoryCategory,
    @get:Schema(title = "是否公开")
    val public: Boolean,
    @get:Schema(title = "简要描述")
    val description: String?,
    @get:Schema(title = "仓库配置信息")
    val configuration: RepositoryConfiguration,
    @get:Schema(title = "存储凭证key")
    val storageCredentialsKey: String?,
    @get:Schema(title = "创建者")
    val createdBy: String,
    @get:Schema(title = "创建日期")
    val createdDate: String,
    @get:Schema(title = "上次修改者")
    val lastModifiedBy: String,
    @get:Schema(title = "上次修改日期")
    val lastModifiedDate: String,
    @get:Schema(title = "仓库配额")
    val quota: Long?,
    @get:Schema(title = "仓库已使用容量")
    val used: Long?,
    @get:Schema(title = "是否展示")
    val display: Boolean
)
