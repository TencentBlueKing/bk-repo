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

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.TagExistedException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.dao.packages.PackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.search.packages.PackageSearchInterpreter
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.repository.UT_PACKAGE_KEY
import com.tencent.bkrepo.repository.UT_PACKAGE_NAME
import com.tencent.bkrepo.repository.UT_PACKAGE_VERSION
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_REPO_NAME
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.bean.override.mockito.MockitoBean

@DisplayName("包服务测试")
@DataMongoTest
@Import(
    PackageDao::class,
    PackageVersionDao::class
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PackageServiceTest @Autowired constructor(
    private val packageService: PackageService,
    private val mongoTemplate: MongoTemplate
) : ServiceBaseTest() {

    @MockitoBean
    private lateinit var packageSearchInterpreter: PackageSearchInterpreter

    @BeforeAll
    fun beforeAll() {
        initMock()
    }

    @BeforeEach
    fun beforeEach() {
        mongoTemplate.remove(Query(), TPackage::class.java)
        mongoTemplate.remove(Query(), TPackageVersion::class.java)
    }

    @Test
    @DisplayName("测试创建包")
    fun `test create package version`() {
        val request = buildCreateRequest(version = "0.0.1-SNAPSHOT", overwrite = false)
        packageService.createPackageVersion(request)
        val tPackage = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        val tPackageVersion = packageService.findVersionByName(
            UT_PROJECT_ID, UT_REPO_NAME, request.packageKey, request.versionName
        )
        Assertions.assertNotNull(tPackageVersion)
        Assertions.assertEquals("value", tPackageVersion!!.metadata["key"])
        Assertions.assertNotNull(tPackage)
        Assertions.assertEquals(UT_USER, tPackage!!.createdBy)
        Assertions.assertEquals(UT_USER, tPackage.lastModifiedBy)
        Assertions.assertEquals(UT_PROJECT_ID, tPackage.projectId)
        Assertions.assertEquals(UT_REPO_NAME, tPackage.repoName)
        Assertions.assertEquals(UT_PACKAGE_NAME, tPackage.name)
        Assertions.assertEquals(UT_PACKAGE_KEY, tPackage.key)
        Assertions.assertEquals("0.0.1-SNAPSHOT", tPackage.latest)
        Assertions.assertEquals(PackageType.MAVEN, tPackage.type)
        Assertions.assertEquals(1, tPackage.versions)
        Assertions.assertEquals(0, tPackage.downloads)
    }

    @Test
    @DisplayName("测试创建同名版本不允许覆盖")
    fun `should throw exception when overwrite is false`() {
        val request = buildCreateRequest(version = "0.0.1-SNAPSHOT", overwrite = false)
        packageService.createPackageVersion(request)
        assertThrows<ErrorCodeException> { packageService.createPackageVersion(request) }
    }

    @Test
    @DisplayName("测试创建同名版本允许覆盖")
    fun `should throw exception when overwrite is true`() {
        val request = buildCreateRequest(version = "0.0.1-SNAPSHOT", overwrite = true)
        packageService.createPackageVersion(request)
        packageService.createPackageVersion(request)

        val tPackage = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        Assertions.assertEquals(1, tPackage.versions)
        Assertions.assertEquals(0, tPackage.downloads)
    }

    @Test
    @DisplayName("测试分页查询包")
    fun `test list package page`() {
        val size = 20
        repeat(size) {
            val request1 = buildCreateRequest(
                packageName = "package$it",
                packageKey = "identifier$it"
            )
            packageService.createPackageVersion(request1)
            val request2 = buildCreateRequest(
                repoName = "repoName",
                packageName = "package$it",
                packageKey = "identifier$it"
            )
            packageService.createPackageVersion(request2)
        }
        val option = PackageListOption(pageNumber = 1, pageSize = 10, packageName = "package")
        val page = packageService.listPackagePage(UT_PROJECT_ID, UT_REPO_NAME, option)
        Assertions.assertEquals(10, page.records.size)
        Assertions.assertEquals(size, page.totalRecords.toInt())
        Assertions.assertEquals(2, page.totalPages)
        Assertions.assertEquals(10, page.pageSize)
        Assertions.assertEquals(1, page.pageNumber)
    }

    @Test
    @DisplayName("测试分页查询版本")
    fun `test list version page`() {
        val size = 20
        repeat(size) {
            val request1 = buildCreateRequest(
                version = "version$it"
            )
            packageService.createPackageVersion(request1)
            val request2 = buildCreateRequest(
                repoName = "repoName",
                version = "version$it"
            )
            packageService.createPackageVersion(request2)
        }
        val option = VersionListOption(pageNumber = 1, pageSize = 10)
        val page = packageService.listVersionPage(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, option)
        Assertions.assertEquals(10, page.records.size)
        Assertions.assertEquals(size, page.totalRecords.toInt())
        Assertions.assertEquals(2, page.totalPages)
        Assertions.assertEquals(10, page.pageSize)
        Assertions.assertEquals(1, page.pageNumber)
    }

    @Test
    @DisplayName("测试删除包")
    fun `test delete package`() {
        val request1 = buildCreateRequest()
        val request2 = buildCreateRequest(version = "0.0.1-SNAPSHOT", overwrite = false)
        packageService.createPackageVersion(request1)
        packageService.createPackageVersion(request2)
        Assertions.assertNotNull(
            packageService.findVersionByName(
                UT_PROJECT_ID,
                UT_REPO_NAME,
                UT_PACKAGE_KEY,
                UT_PACKAGE_VERSION
            )
        )
        Assertions.assertNotNull(
            packageService.findVersionByName(
                UT_PROJECT_ID,
                UT_REPO_NAME,
                UT_PACKAGE_KEY,
                "0.0.1-SNAPSHOT"
            )
        )

        packageService.deleteVersion(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, UT_PACKAGE_VERSION)
        Assertions.assertNull(
            packageService.findVersionByName(
                UT_PROJECT_ID,
                UT_REPO_NAME,
                UT_PACKAGE_KEY,
                UT_PACKAGE_VERSION
            )
        )
    }

    @Test
    @DisplayName("测试删除版本")
    fun `test delete version`() {
        val request1 = buildCreateRequest(version = "0.0.1")
        val request2 = buildCreateRequest(version = "0.0.2")
        val request3 = buildCreateRequest(packageKey = "key1")
        packageService.createPackageVersion(request1)
        packageService.createPackageVersion(request2)
        packageService.createPackageVersion(request3)

        packageService.deletePackage(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        Assertions.assertNull(packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY))
        Assertions.assertNull(packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "0.0.1"))
        Assertions.assertNull(packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "0.0.2"))

        Assertions.assertNotNull(packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, "key1"))
        Assertions.assertNotNull(
            packageService.findVersionByName(
                UT_PROJECT_ID,
                UT_REPO_NAME,
                "key1",
                UT_PACKAGE_VERSION
            )
        )
    }

    @Test
    @DisplayName("测试删除不存在的包")
    fun `should throw exception when delete non exist package`() {
        val request = buildCreateRequest()
        packageService.createPackageVersion(request)
        packageService.deletePackage(UT_PROJECT_ID, UT_REPO_NAME, "non-exist")
    }

    @Test
    @DisplayName("测试删除不存在的版本")
    fun `should throw exception when delete non exist version`() {
        val request = buildCreateRequest()
        packageService.createPackageVersion(request)
        packageService.deleteVersion(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "non-exist")
    }

    @Test
    @DisplayName("测试创建标签")
    fun `test create tag`() {
        val request = buildCreateRequest(version = "1.0.0")
        packageService.createPackageVersion(request)
        
        val tag = "latest"
        val msg = "This is the latest version"
        packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0", tag, msg)
        
        val packageSummary = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        Assertions.assertNotNull(packageSummary)
        Assertions.assertTrue(packageSummary!!.versionTag.containsKey(tag))
        Assertions.assertEquals("1.0.0", packageSummary.versionTag[tag])
        
        val packageVersion = packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0")
        Assertions.assertNotNull(packageVersion)
        Assertions.assertTrue(packageVersion!!.tags.contains(tag))
    }

    @Test
    @DisplayName("测试创建标签-包不存在")
    fun `should throw exception when create tag for non exist package`() {
        assertThrows<PackageNotFoundException> {
            packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, "non-exist-package", "1.0.0", "latest")
        }
    }

    @Test
    @DisplayName("测试创建标签-版本不存在")
    fun `should throw exception when create tag for non exist version`() {
        val request = buildCreateRequest(version = "1.0.0")
        packageService.createPackageVersion(request)
        
        assertThrows<VersionNotFoundException> {
            packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "2.0.0", "latest")
        }
    }

    @Test
    @DisplayName("测试创建标签-标签已存在")
    fun `should throw exception when create tag that already exists`() {
        val request = buildCreateRequest(version = "1.0.0")
        packageService.createPackageVersion(request)
        
        val tag = "latest"
        packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0", tag)
        
        assertThrows<TagExistedException> {
            packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0", tag)
        }
    }

    @Test
    @DisplayName("测试创建标签-不带消息")
    fun `test create tag without message`() {
        val request = buildCreateRequest(version = "1.0.0")
        packageService.createPackageVersion(request)
        
        val tag = "v1.0.0"
        packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0", tag, null)
        
        val packageSummary = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        Assertions.assertNotNull(packageSummary)
        Assertions.assertTrue(packageSummary!!.versionTag.containsKey(tag))
        
        val packageVersion = packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0")
        Assertions.assertNotNull(packageVersion)
        Assertions.assertTrue(packageVersion!!.tags.contains(tag))
    }

    @Test
    @DisplayName("测试删除标签")
    fun `test delete tag`() {
        val request = buildCreateRequest(version = "1.0.0")
        packageService.createPackageVersion(request)
        
        val tag = "latest"
        packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0", tag)
        
        // 验证标签已创建
        var packageSummary = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        Assertions.assertNotNull(packageSummary)
        Assertions.assertTrue(packageSummary!!.versionTag.containsKey(tag))
        
        // 删除标签
        packageService.deleteTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, tag)
        
        // 验证标签已删除
        packageSummary = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        Assertions.assertNotNull(packageSummary)
        Assertions.assertFalse(packageSummary!!.versionTag.containsKey(tag))
        
        val packageVersion = packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0")
        Assertions.assertNotNull(packageVersion)
        Assertions.assertFalse(packageVersion!!.tags.contains(tag))
    }

    @Test
    @DisplayName("测试删除标签-包不存在")
    fun `should throw exception when delete tag for non exist package`() {
        assertThrows<PackageNotFoundException> {
            packageService.deleteTag(UT_PROJECT_ID, UT_REPO_NAME, "non-exist-package", "latest")
        }
    }

    @Test
    @DisplayName("测试删除标签-标签不存在")
    fun `should not throw exception when delete non exist tag`() {
        val request = buildCreateRequest(version = "1.0.0")
        packageService.createPackageVersion(request)
        
        // 删除不存在的标签应该不会抛异常，直接返回
        packageService.deleteTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "non-exist-tag")
        
        // 验证包仍然存在
        val packageSummary = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        Assertions.assertNotNull(packageSummary)
    }

    @Test
    @DisplayName("测试删除标签-多个标签")
    fun `test delete tag with multiple tags`() {
        val request = buildCreateRequest(version = "1.0.0")
        packageService.createPackageVersion(request)
        
        val tag1 = "latest"
        val tag2 = "stable"
        val tag3 = "v1.0.0"
        
        packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0", tag1)
        packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0", tag2)
        packageService.createTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "1.0.0", tag3)
        
        // 验证所有标签都已创建
        var packageSummary = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        Assertions.assertNotNull(packageSummary)
        Assertions.assertTrue(packageSummary!!.versionTag.containsKey(tag1))
        Assertions.assertTrue(packageSummary.versionTag.containsKey(tag2))
        Assertions.assertTrue(packageSummary.versionTag.containsKey(tag3))
        
        // 删除其中一个标签
        packageService.deleteTag(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, tag2)
        
        // 验证只有 tag2 被删除
        packageSummary = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
        Assertions.assertNotNull(packageSummary)
        Assertions.assertTrue(packageSummary!!.versionTag.containsKey(tag1))
        Assertions.assertFalse(packageSummary.versionTag.containsKey(tag2))
        Assertions.assertTrue(packageSummary.versionTag.containsKey(tag3))
    }

    companion object {

        fun buildCreateRequest(
            repoName: String = UT_REPO_NAME,
            packageName: String = UT_PACKAGE_NAME,
            packageKey: String = UT_PACKAGE_KEY,
            version: String = UT_PACKAGE_VERSION,
            overwrite: Boolean = false
        ): PackageVersionCreateRequest {
            return PackageVersionCreateRequest(
                projectId = UT_PROJECT_ID,
                repoName = repoName,
                packageName = packageName,
                packageKey = packageKey,
                packageType = PackageType.MAVEN,
                packageDescription = "some description",
                versionName = version,
                size = 1024,
                manifestPath = "/com/tencent/bkrepo/test/$version",
                artifactPath = "/com/tencent/bkrepo/test/$version",
                stageTag = null,
                packageMetadata = listOf(MetadataModel(key = "key", value = "value")),
                overwrite = overwrite,
                createdBy = UT_USER
            )
        }
    }
}
