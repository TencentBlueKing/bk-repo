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

package com.tencent.bkrepo.replication.replica.repository.internal.type

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion

class NpmPackageNodeMapper : PackageNodeMapper {

    override fun type() = RepositoryType.NPM
    override fun extraType(): RepositoryType? {
        return null
    }

    override fun map(
        packageSummary: PackageSummary,
        packageVersion: PackageVersion,
        type: RepositoryType
    ): List<String> {
        val name = if (type == RepositoryType.OHPM) {
            PackageKeys.resolveOhpm(packageSummary.key)
        } else {
            PackageKeys.resolveNpm(packageSummary.key)
        }
        val version = packageVersion.name
        return listOf(
            NPM_PKG_TGZ_FULL_PATH.format(name, name, version),
            NPM_PKG_VERSION_METADATA_FULL_PATH.format(name, name, version),
            NPM_PKG_METADATA_FULL_PATH.format(name)
        )
    }

    companion object {
        const val NPM_PKG_TGZ_FULL_PATH = "/%s/-/%s-%s.tgz"
        const val NPM_PKG_VERSION_METADATA_FULL_PATH = "/.npm/%s/%s-%s.json"
        const val NPM_PKG_METADATA_FULL_PATH = "/.npm/%s/package.json"
    }
}
