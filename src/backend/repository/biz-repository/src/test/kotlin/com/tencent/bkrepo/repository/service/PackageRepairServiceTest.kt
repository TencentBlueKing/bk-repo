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

import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.dao.packages.PackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.search.packages.PackageSearchInterpreter
import com.tencent.bkrepo.common.metadata.service.packages.PackageRepairService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.repository.UT_PACKAGE_KEY
import com.tencent.bkrepo.repository.UT_PACKAGE_NAME
import com.tencent.bkrepo.repository.UT_PACKAGE_VERSION
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_REPO_NAME
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.bean.override.mockito.MockitoBean

@DisplayName("Package 元数据修复测试")
@DataMongoTest
@Import(
    PackageDao::class,
    PackageVersionDao::class
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PackageRepairServiceTest @Autowired constructor(
    private val packageService: PackageService,
    private val packageRepairService: PackageRepairService,
    private val packageDao: PackageDao,
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
    @DisplayName("latest 指向已删除版本时修复后指向 ordinal 最大的现存版本")
    fun `should repair latest when it points to a deleted version`() {
        // 创建 3 个版本：1.0.0 / 1.0.1 / 1.0.2
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.0"))
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.1"))
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.2"))
        // 此时 latest = 1.0.2

        // 手动删除 1.0.2 版本记录但不动 TPackage
        // 注意：packageService.findVersionByName 返回的是 pojo PackageVersion，仅暴露 name 等展示字段，
        // 没有 mongo _id，因此这里通过 packageId + name 组合唯一定位 TPackageVersion 后删除。
        val ownerPkg = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        mongoTemplate.remove(
            Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("packageId").`is`(ownerPkg.id)
                    .and("name").`is`("1.0.2")
            ),
            TPackageVersion::class.java
        )
        // 制造脏数据：latest 依然指向已删除的 1.0.2
        val beforePkg = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        Assertions.assertEquals("1.0.2", beforePkg.latest)

        val result = packageRepairService.repairPackageMetadata(
            UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY
        )
        Assertions.assertEquals(1, result.total)
        Assertions.assertEquals(1, result.updated)
        Assertions.assertEquals(0, result.skipped)
        Assertions.assertEquals(0, result.failed)

        val afterPkg = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        Assertions.assertEquals("1.0.1", afterPkg.latest)
        Assertions.assertEquals(setOf("1.0.0", "1.0.1"), afterPkg.historyVersion)
    }

    @Test
    @DisplayName("historyVersion 含脏数据时修复后与 package_version 一致")
    fun `should fully overwrite historyVersion and clean dirty entries`() {
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.0"))
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.1"))
        // 手动往 historyVersion 里塞脏数据 "ghost-x"
        val beforePkg = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        beforePkg.historyVersion = beforePkg.historyVersion + "ghost-x" + "ghost-y"
        packageDao.save(beforePkg)

        val dirtyPkg = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        Assertions.assertTrue(dirtyPkg.historyVersion.contains("ghost-x"))

        val result = packageRepairService.repairPackageMetadata(
            UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY
        )
        Assertions.assertEquals(1, result.updated)

        val afterPkg = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        Assertions.assertEquals(setOf("1.0.0", "1.0.1"), afterPkg.historyVersion)
        Assertions.assertFalse(afterPkg.historyVersion.contains("ghost-x"))
        Assertions.assertFalse(afterPkg.historyVersion.contains("ghost-y"))
    }

    @Test
    @DisplayName("元数据已一致时不发生更新，计入 skipped")
    fun `should skip when metadata already consistent`() {
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.0"))
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.1"))

        val result = packageRepairService.repairPackageMetadata(
            UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY
        )
        Assertions.assertEquals(1, result.total)
        Assertions.assertEquals(0, result.updated)
        Assertions.assertEquals(1, result.skipped)
        Assertions.assertEquals(0, result.failed)
    }

    @Test
    @DisplayName("指定 packageKey 时仅修复目标 package，其他包不受影响")
    fun `should only repair target package when packageKey specified`() {
        packageService.createPackageVersion(buildCreateRequest(packageKey = "helm://pkg-a", version = "1.0.0"))
        packageService.createPackageVersion(buildCreateRequest(packageKey = "helm://pkg-a", version = "1.0.1"))
        packageService.createPackageVersion(buildCreateRequest(packageKey = "helm://pkg-b", version = "2.0.0"))
        packageService.createPackageVersion(buildCreateRequest(packageKey = "helm://pkg-b", version = "2.0.1"))

        // 给两个包都塞脏数据
        listOf("helm://pkg-a", "helm://pkg-b").forEach { key ->
            val p = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, key)!!
            p.historyVersion = p.historyVersion + "dirty"
            packageDao.save(p)
        }

        val result = packageRepairService.repairPackageMetadata(
            UT_PROJECT_ID, UT_REPO_NAME, "helm://pkg-a"
        )
        Assertions.assertEquals(1, result.total)
        Assertions.assertEquals(1, result.updated)

        val pkgA = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, "helm://pkg-a")!!
        val pkgB = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, "helm://pkg-b")!!
        Assertions.assertFalse(pkgA.historyVersion.contains("dirty"))
        Assertions.assertTrue(pkgB.historyVersion.contains("dirty"), "pkg-b 未指定，脏数据应保留")
    }

    @Test
    @DisplayName("不传 packageKey 时修复仓库下全部 package")
    fun `should repair all packages in repo when packageKey is null`() {
        packageService.createPackageVersion(buildCreateRequest(packageKey = "helm://pkg-a", version = "1.0.0"))
        packageService.createPackageVersion(buildCreateRequest(packageKey = "helm://pkg-b", version = "2.0.0"))
        packageService.createPackageVersion(buildCreateRequest(packageKey = "helm://pkg-c", version = "3.0.0"))

        // 给全部包塞脏数据
        listOf("helm://pkg-a", "helm://pkg-b", "helm://pkg-c").forEach { key ->
            val p = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, key)!!
            p.historyVersion = p.historyVersion + "dirty"
            packageDao.save(p)
        }

        val result = packageRepairService.repairPackageMetadata(
            UT_PROJECT_ID, UT_REPO_NAME, null
        )
        Assertions.assertEquals(3, result.total)
        Assertions.assertEquals(3, result.updated)
        Assertions.assertEquals(0, result.skipped)
        Assertions.assertEquals(0, result.failed)

        listOf("helm://pkg-a", "helm://pkg-b", "helm://pkg-c").forEach { key ->
            val p = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, key)!!
            Assertions.assertFalse(p.historyVersion.contains("dirty"))
        }
    }

    @Test
    @DisplayName("修复范围严格限定在指定仓库，不影响同项目其他仓库")
    fun `should not touch packages in other repos`() {
        packageService.createPackageVersion(
            buildCreateRequest(repoName = UT_REPO_NAME, version = "1.0.0")
        )
        packageService.createPackageVersion(
            buildCreateRequest(repoName = "other-repo", version = "1.0.0")
        )

        // 给"其他仓库"的包塞脏数据
        val otherPkg = packageDao.findByKey(UT_PROJECT_ID, "other-repo", UT_PACKAGE_KEY)!!
        otherPkg.historyVersion = otherPkg.historyVersion + "dirty"
        packageDao.save(otherPkg)

        val result = packageRepairService.repairPackageMetadata(
            UT_PROJECT_ID, UT_REPO_NAME, null
        )
        // 只处理 UT_REPO_NAME 下的 package
        Assertions.assertEquals(1, result.total)

        val stillDirty = packageDao.findByKey(UT_PROJECT_ID, "other-repo", UT_PACKAGE_KEY)!!
        Assertions.assertTrue(stillDirty.historyVersion.contains("dirty"), "跨仓库不应被修复")
    }

    @Test
    @DisplayName("指定不存在的 packageKey 时返回空结果")
    fun `should return empty result when packageKey not found`() {
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.0"))

        val result = packageRepairService.repairPackageMetadata(
            UT_PROJECT_ID, UT_REPO_NAME, "non-exist"
        )
        Assertions.assertEquals(0, result.total)
        Assertions.assertEquals(0, result.updated)
        Assertions.assertEquals(0, result.skipped)
        Assertions.assertEquals(0, result.failed)
    }

    @Test
    @DisplayName("当 package 已无任何版本时 latest 被清空")
    fun `should unset latest when package has no version left`() {
        packageService.createPackageVersion(buildCreateRequest(version = "1.0.0"))
        // 删掉唯一版本，只保留 TPackage
        mongoTemplate.remove(Query(), TPackageVersion::class.java)

        val before = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        Assertions.assertEquals("1.0.0", before.latest)

        val result = packageRepairService.repairPackageMetadata(
            UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY
        )
        Assertions.assertEquals(1, result.updated)

        val after = packageDao.findByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)!!
        Assertions.assertNull(after.latest)
        Assertions.assertTrue(after.historyVersion.isEmpty())
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
