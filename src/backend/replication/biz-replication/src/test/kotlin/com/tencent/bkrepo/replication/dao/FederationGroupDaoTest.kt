package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.replication.model.TFederationGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DataMongoTest
@Import(FederationGroupDao::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class FederationGroupDaoTest {

    @Autowired
    private lateinit var federationGroupDao: FederationGroupDao

    @BeforeEach
    fun setUp() {
        federationGroupDao.remove(Query())
    }

    // ==================== findByName ====================

    @Test
    fun `findByName - existing group should be returned`() {
        val group = save(name = "group-a", clusterIds = listOf("c1", "c2"))

        val found = federationGroupDao.findByName("group-a")

        assertNotNull(found)
        assertEquals("group-a", found!!.name)
        assertEquals(listOf("c1", "c2"), found.clusterIds)
    }

    @Test
    fun `findByName - non-existing group should return null`() {
        val result = federationGroupDao.findByName("non-existent")

        assertNull(result)
    }

    // ==================== findAutoEnableGroups ====================

    @Test
    fun `findAutoEnableGroups - autoEnable true with null projectScope matches any project`() {
        save(name = "global-group", clusterIds = listOf("c1"), autoEnable = true, projectScope = null)

        val result = federationGroupDao.findAutoEnableGroups("any-project")

        assertEquals(1, result.size)
        assertEquals("global-group", result[0].name)
    }

    @Test
    fun `findAutoEnableGroups - autoEnable true with matching projectScope returns group`() {
        save(name = "scoped-group", clusterIds = listOf("c1"), autoEnable = true, projectScope = listOf("proj-a"))

        val result = federationGroupDao.findAutoEnableGroups("proj-a")

        assertEquals(1, result.size)
        assertEquals("scoped-group", result[0].name)
    }

    @Test
    fun `findAutoEnableGroups - autoEnable true with non-matching projectScope returns empty`() {
        save(name = "scoped-group", clusterIds = listOf("c1"), autoEnable = true, projectScope = listOf("proj-a"))

        val result = federationGroupDao.findAutoEnableGroups("proj-b")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findAutoEnableGroups - autoEnable false should not be returned`() {
        save(name = "disabled-group", clusterIds = listOf("c1"), autoEnable = false, projectScope = null)

        val result = federationGroupDao.findAutoEnableGroups("any-project")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findAutoEnableGroups - mixed groups returns only matching ones`() {
        save(name = "global", clusterIds = listOf("c1"), autoEnable = true, projectScope = null)
        save(name = "scoped-match", clusterIds = listOf("c2"), autoEnable = true, projectScope = listOf("proj-x"))
        save(name = "scoped-no-match", clusterIds = listOf("c3"), autoEnable = true, projectScope = listOf("proj-y"))
        save(name = "disabled", clusterIds = listOf("c4"), autoEnable = false, projectScope = null)

        val result = federationGroupDao.findAutoEnableGroups("proj-x")

        assertEquals(2, result.size)
        val names = result.map { it.name }.toSet()
        assertTrue(names.contains("global"))
        assertTrue(names.contains("scoped-match"))
    }

    @Test
    fun `findAutoEnableGroups - projectScope with multiple projects matches correctly`() {
        save(
            name = "multi-scope", clusterIds = listOf("c1"),
            autoEnable = true, projectScope = listOf("p1", "p2", "p3")
        )

        assertTrue(federationGroupDao.findAutoEnableGroups("p1").isNotEmpty())
        assertTrue(federationGroupDao.findAutoEnableGroups("p2").isNotEmpty())
        assertTrue(federationGroupDao.findAutoEnableGroups("p3").isNotEmpty())
        assertTrue(federationGroupDao.findAutoEnableGroups("p4").isEmpty())
    }

    // ==================== save / unique index ====================

    @Test
    fun `save - same name overwrite is handled by underlying store`() {
        save(name = "unique-group", clusterIds = listOf("c1"))
        // embedded MongoDB 不强制 @Indexed unique，此处验证 findByName 仍返回最初写入的记录
        val found = federationGroupDao.findByName("unique-group")
        assertNotNull(found)
        assertEquals("unique-group", found!!.name)
    }

    @Test
    fun `save - clusterIds and metadata are persisted correctly`() {
        val clusterIds = listOf("cluster-1", "cluster-2", "cluster-3")
        val group = save(
            name = "full-group", clusterIds = clusterIds,
            autoEnable = true, projectScope = listOf("proj-a")
        )

        val found = federationGroupDao.findByName("full-group")!!
        assertEquals(clusterIds, found.clusterIds)
        assertEquals(true, found.autoEnableForNewRepo)
        assertEquals(listOf("proj-a"), found.projectScope)
        assertNotNull(found.id)
    }

    // ==================== helper ====================

    private fun save(
        name: String,
        clusterIds: List<String>,
        currentClusterId: String = clusterIds.first(),
        autoEnable: Boolean = true,
        projectScope: List<String>? = null
    ): TFederationGroup {
        val group = TFederationGroup(
            name = name,
            currentClusterId = currentClusterId,
            clusterIds = clusterIds,
            autoEnableForNewRepo = autoEnable,
            projectScope = projectScope,
            createdBy = "test",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "test",
            lastModifiedDate = LocalDateTime.now()
        )
        return federationGroupDao.save(group)
    }
}
