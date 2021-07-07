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

package com.tencent.bkrepo.replication.mapping

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.PackageKeys

class MavenPackageNodeMapper : PackageNodeMapper {

    override fun type() = RepositoryType.MAVEN

    override fun map(key: String, version: String, extension: Map<String, Any>): List<String> {
        val gavKey = PackageKeys.resolveGav(key)
        val groupId = gavKey.substringBefore(StringPool.COLON).replace(StringPool.DOT, StringPool.SLASH)
        val artifactId = gavKey.substringAfter(StringPool.COLON)
        return when (val packageType = extension[PACKAGE_TYPE] as? String) {
            "pom" -> {
                listOf(
                    POM_FULL_PATH.format(groupId, artifactId, version, artifactId, version),
                    POM_MD5_FULL_PATH.format(groupId, artifactId, version, artifactId, version),
                    POM_SHA1_FULL_PATH.format(groupId, artifactId, version, artifactId, version)
                )
            }
            else -> listOf(
                JAR_FULL_PATH.format(groupId, artifactId, version, artifactId, version, packageType),
                JAR_MD5_FULL_PATH.format(groupId, artifactId, version, artifactId, version, packageType),
                JAR_SHA1_FULL_PATH.format(groupId, artifactId, version, artifactId, version, packageType),
                POM_FULL_PATH.format(groupId, artifactId, version, artifactId, version),
                POM_MD5_FULL_PATH.format(groupId, artifactId, version, artifactId, version),
                POM_SHA1_FULL_PATH.format(groupId, artifactId, version, artifactId, version)
            )
        }
    }

    companion object {
        const val PACKAGE_TYPE = "packaging"

        // groupId/artifactId/version/artifactId-version.xxx
        const val JAR_FULL_PATH = "%s/%s/%s/%s-%s.%s"
        const val JAR_MD5_FULL_PATH = "%s/%s/%s/%s-%s.%s.md5"
        const val JAR_SHA1_FULL_PATH = "%s/%s/%s/%s-%s.%s.sha1"
        const val POM_FULL_PATH = "%s/%s/%s/%s-%s.pom"
        const val POM_MD5_FULL_PATH = "%s/%s/%s/%s-%s.pom.md5"
        const val POM_SHA1_FULL_PATH = "%s/%s/%s/%s-%s.pom.sha1"
    }
}
