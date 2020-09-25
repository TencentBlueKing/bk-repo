package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.UT_PACKAGE_KEY
import com.tencent.bkrepo.repository.UT_PACKAGE_NAME
import com.tencent.bkrepo.repository.UT_PACKAGE_VERSION
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_REPO_NAME
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.dao.PackageDao
import com.tencent.bkrepo.repository.dao.PackageVersionDao
import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.stage.ArtifactStageEnum
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

@DisplayName("包服务测试")
@DataMongoTest
@Import(
    PackageDao::class,
    PackageVersionDao::class
)
class PackageServiceTest @Autowired constructor(
    private val packageService: PackageService,
    private val mongoTemplate: MongoTemplate
) : ServiceBaseTest() {

    @BeforeEach
    fun beforeEach() {
        initMock()
        mongoTemplate.remove(Query(), TPackage::class.java)
        mongoTemplate.remove(Query(), TPackageVersion::class.java)
    }

    @Test
    @DisplayName("测试创建包")
    fun `test create package version`() {
        val request = buildCreateRequest(version = "0.0.1-SNAPSHOT", overwrite = false)
        packageService.createPackageVersion(request)
        val tPackage = packageService.findPackageByKey(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY)
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
        val page = packageService.listPackagePageByName(UT_PROJECT_ID, UT_REPO_NAME, "package", 1, 10)
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
        val page = packageService.listVersionPage(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, null, null, 1, 10)
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
        Assertions.assertNotNull(packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, UT_PACKAGE_VERSION))
        Assertions.assertNotNull(packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "0.0.1-SNAPSHOT"))

        packageService.deleteVersion(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, UT_PACKAGE_VERSION)
        assertThrows<ErrorCodeException> {
            packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, UT_PACKAGE_VERSION)
        }
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
        Assertions.assertNotNull(packageService.findVersionByName(UT_PROJECT_ID, UT_REPO_NAME, "key1", UT_PACKAGE_VERSION))
    }

    @Test
    @DisplayName("测试删除不存在的包")
    fun `should throw exception when delete non exist package`() {
        val request = buildCreateRequest()
        packageService.createPackageVersion(request)
        assertThrows<ErrorCodeException> {  packageService.deletePackage(UT_PROJECT_ID, UT_REPO_NAME, "non-exist") }
    }

    @Test
    @DisplayName("测试删除不存在的版本")
    fun `should throw exception when delete non exist version`() {
        val request = buildCreateRequest()
        packageService.createPackageVersion(request)
        assertThrows<ErrorCodeException> {  packageService.deleteVersion(UT_PROJECT_ID, UT_REPO_NAME, UT_PACKAGE_KEY, "non-exist") }
    }

    private fun buildCreateRequest(
        projectId: String = UT_PROJECT_ID,
        repoName: String = UT_REPO_NAME,
        packageName: String = UT_PACKAGE_NAME,
        packageKey: String = UT_PACKAGE_KEY,
        version: String = UT_PACKAGE_VERSION,
        overwrite: Boolean = false
    ): PackageVersionCreateRequest {
        return PackageVersionCreateRequest(
            projectId = projectId,
            repoName = repoName,
            packageName = packageName,
            packageKey = packageKey,
            packageType = PackageType.MAVEN,
            packageDescription = "some description",
            versionName = version,
            size = 1024,
            manifestPath = "/com/tencent/bkrepo/test/$version",
            contentPath = "/com/tencent/bkrepo/test/$version",
            stageTag = listOf(ArtifactStageEnum.RELEASE.toString() ),
            metadata = mapOf("key" to "value"),
            overwrite = overwrite,
            createdBy = UT_USER
        )
    }
}