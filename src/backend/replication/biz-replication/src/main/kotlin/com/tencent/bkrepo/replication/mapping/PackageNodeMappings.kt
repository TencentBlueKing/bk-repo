/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.mapping

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType

/**
 * 包和节点的映射关系
 */
object PackageNodeMappings {

    private val mappers = mutableMapOf<RepositoryType, PackageNodeMapper>()

    init {
        addMapper(MavenPackageNodeMapper())
        addMapper(NpmPackageNodeMapper())
    }

    private fun addMapper(mapper: PackageNodeMapper) {
        mappers[mapper.type()] = mapper
    }

    /**
     * @param type 仓库类型
     * @param key 包名
     * @param version 版本名称
     * @param extension 扩展属性
     *
     * @return 返回
     */
    fun map(type: RepositoryType, key: String, version: String, extension: Map<String, Any>): List<String> {
        val mapper = mappers[type]
        check(mapper != null) { "mapper[$type] not found" }
        return mapper.map(key, version, extension)
    }
}
