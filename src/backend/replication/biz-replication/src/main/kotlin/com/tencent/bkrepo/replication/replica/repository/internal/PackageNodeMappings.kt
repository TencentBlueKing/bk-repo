/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.repository.internal

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.replica.repository.internal.type.DockerPackageNodeMapper
import com.tencent.bkrepo.replication.replica.repository.internal.type.HelmPackageNodeMapper
import com.tencent.bkrepo.replication.replica.repository.internal.type.MavenPackageNodeMapper
import com.tencent.bkrepo.replication.replica.repository.internal.type.NpmPackageNodeMapper
import com.tencent.bkrepo.replication.replica.repository.internal.type.PackageNodeMapper
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion

/**
 * 包和节点的映射关系
 */
object PackageNodeMappings {

    private val mappers = mutableMapOf<RepositoryType, PackageNodeMapper>()

    init {
        addMapper(SpringContextUtils.getBean(MavenPackageNodeMapper::class.java))
        addMapper(NpmPackageNodeMapper())
        addMapper(SpringContextUtils.getBean(HelmPackageNodeMapper::class.java))
        addMapper(SpringContextUtils.getBean(DockerPackageNodeMapper::class.java))
    }

    private fun addMapper(mapper: PackageNodeMapper) {
        mappers[mapper.type()] = mapper
        mapper.extraType()?.let { mappers[mapper.extraType()!!] = mapper }
    }

    /**
     * @param packageSummary 包信息总览
     * @param packageVersion 版本信息
     * @param type 仓库类型
     *
     * @return 返回
     */
    fun map(
        packageSummary: PackageSummary,
        packageVersion: PackageVersion,
        type: RepositoryType
    ): List<String> {
        val mapper = mappers[type]
        check(mapper != null) { "mapper[$type] not found" }
        return mapper.map(packageSummary, packageVersion, type)
    }
}
