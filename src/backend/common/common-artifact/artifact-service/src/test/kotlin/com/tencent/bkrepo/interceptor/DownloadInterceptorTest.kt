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

package com.tencent.bkrepo.interceptor

import com.tencent.bkrepo.common.artifact.constant.DownloadInterceptorType.PACKAGE_FORBID
import com.tencent.bkrepo.common.artifact.constant.FORBID_STATUS
import com.tencent.bkrepo.common.artifact.exception.ArtifactDownloadForbiddenException
import com.tencent.bkrepo.common.metadata.interceptor.DownloadInterceptorFactory
import com.tencent.bkrepo.common.metadata.interceptor.impl.FilenameInterceptor
import com.tencent.bkrepo.common.metadata.interceptor.impl.NodeMetadataInterceptor
import com.tencent.bkrepo.common.metadata.interceptor.impl.WebInterceptor
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class DownloadInterceptorTest {

    @Test
    @DisplayName("文件名下载拦截器测试")
    fun filenameTest() {
        val invalidRule = mapOf<String, Any>(
            "k" to ""
        )
        val passRule = mapOf<String, Any>(
            FILENAME to "*.txt"
        )
        val forbiddenRule = mapOf<String, Any>(
            FILENAME to "*.apk"
        )
        val nodeDetail = nodeDetail("test.txt")
        assertDoesNotThrow { FilenameInterceptor(invalidRule).intercept(nodeDetail.projectId, nodeDetail) }
        assertDoesNotThrow { FilenameInterceptor(passRule).intercept(nodeDetail.projectId, nodeDetail) }
        assertThrows<ArtifactDownloadForbiddenException> {
            FilenameInterceptor(forbiddenRule).intercept(nodeDetail.projectId, nodeDetail)
        }
    }

    @Test
    @DisplayName("元数据下载拦截器测试")
    fun metadataTest() {
        val invalidRule = mapOf<String, Any>(
            METADATA to "k：v"
        )
        val passRule = mapOf<String, Any>(
            METADATA to "k1:  v1"
        )
        val forbiddenRule = mapOf<String, Any>(
            METADATA to "k: v"
        )
        val metadata = mapOf(
            "k1" to "v1",
            "k2" to "v2"
        )
        val nodeDetail = nodeDetail("test", metadata)
        assertDoesNotThrow { NodeMetadataInterceptor(invalidRule).intercept(nodeDetail.projectId, nodeDetail) }
        assertDoesNotThrow { NodeMetadataInterceptor(passRule).intercept(nodeDetail.projectId, nodeDetail) }
        assertThrows<ArtifactDownloadForbiddenException> {
            NodeMetadataInterceptor(forbiddenRule).intercept(nodeDetail.projectId, nodeDetail)
        }
    }

    @Test
    @DisplayName("制品禁用下载拦截器测试")
    fun forbidTest() {
        val forbidInterceptor = DownloadInterceptorFactory.buildNodeForbidInterceptor()

        var node = nodeDetail("test", emptyMap())
        assertDoesNotThrow { forbidInterceptor.intercept(node.projectId, node) }
        node = node.copy(metadata = mapOf(FORBID_STATUS to true))
        assertThrows<ArtifactDownloadForbiddenException> { forbidInterceptor.intercept(node.projectId, node) }
    }

    @Test
    @DisplayName("Package禁用下载拦截器测试")
    fun packageForbidTest() {
        val forbidInterceptor = DownloadInterceptorFactory.buildPackageInterceptor(PACKAGE_FORBID)!!

        var packageVersion = packageVersion(mapOf(FORBID_STATUS to false))
        assertDoesNotThrow { forbidInterceptor.intercept("test", packageVersion) }
        packageVersion = packageVersion.copy(metadata = mapOf(FORBID_STATUS to true))
        assertThrows<ArtifactDownloadForbiddenException> { forbidInterceptor.intercept("test", packageVersion) }
    }

    @Test
    @DisplayName("Web端下载拦截器测试")
    fun webTest() {
        val invalidRule = mapOf<String, Any>()
        val passRule = mapOf<String, Any>(
            FILENAME to "*",
            METADATA to "k1:  v1"
        )
        val forbiddenRule = mapOf<String, Any>(
            FILENAME to "**.apk",
            METADATA to "k1:  v1"
        )
        val metadata = mapOf(
            "k1" to "v1",
            "k2" to "v2"
        )
        val nodeDetail = nodeDetail("test.txt", metadata)
        assertDoesNotThrow { WebInterceptor(invalidRule).intercept(nodeDetail.projectId, nodeDetail) }
        assertDoesNotThrow { WebInterceptor(passRule).intercept(nodeDetail.projectId, nodeDetail) }
        assertThrows<ArtifactDownloadForbiddenException> {
            WebInterceptor(forbiddenRule).intercept(nodeDetail.projectId, nodeDetail)
        }
    }

    @Test
    @DisplayName("移动端下载拦截器测试")
    fun mobileTest() {
        val invalidRule = mapOf<String, Any>(
            "k" to "v"
        )
        val passRule = mapOf<String, Any>(
            FILENAME to "**",
            METADATA to "k1:  v1"
        )
        val forbiddenRule = mapOf<String, Any>(
            FILENAME to "**.apk",
            METADATA to "k1:  v1"
        )
        val metadata = mapOf(
            "k1" to "v1",
            "k2" to "v2"
        )
        val nodeDetail = nodeDetail("test", metadata)
        assertDoesNotThrow { WebInterceptor(invalidRule).intercept(nodeDetail.projectId, nodeDetail) }
        assertDoesNotThrow { WebInterceptor(passRule).intercept(nodeDetail.projectId, nodeDetail) }
        assertThrows<ArtifactDownloadForbiddenException> {
            WebInterceptor(forbiddenRule).intercept(nodeDetail.projectId, nodeDetail)
        }
    }

    private fun nodeDetail(name: String, metadata: Map<String, Any>? = null): NodeDetail {
        val path = "/a/b/c"
        val nodeInfo = NodeInfo(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now().toString(),
            lastModifiedBy = UT_USER,
            lastModifiedDate = LocalDateTime.now().toString(),
            lastAccessDate = LocalDateTime.now().toString(),
            folder = false,
            path = path,
            name = name,
            fullPath = "$path/$name",
            size = 1,
            projectId = UT_PROJECT,
            repoName = UT_REPO,
            metadata = metadata
        )
        return NodeDetail(nodeInfo)
    }

    private fun packageVersion(metadata: Map<String, Any> = emptyMap()): PackageVersion {
        return PackageVersion(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = UT_USER,
            lastModifiedDate = LocalDateTime.now(),
            name = "test",
            size = 1L,
            downloads = 1L,
            stageTag = emptyList(),
            metadata = metadata,
            packageMetadata = metadata.map { MetadataModel(it.key, it.value) },
            emptyList(),
            emptyMap(),
            "/test",
            setOf("/test")
        )
    }


    companion object {
        private const val FILENAME = "filename"
        private const val METADATA = "metadata"
        private const val UT_USER = "ut_user"
        private const val UT_PROJECT = "ut_project"
        private const val UT_REPO = "ut_repo"
    }
}
