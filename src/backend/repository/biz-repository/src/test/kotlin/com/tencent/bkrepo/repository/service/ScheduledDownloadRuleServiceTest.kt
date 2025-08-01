package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.DOWNLOAD
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.MANAGE
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.manager.PrincipalManager
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_REPO_NAME
import com.tencent.bkrepo.repository.dao.ScheduledDownloadRuleDao
import com.tencent.bkrepo.repository.pojo.schedule.MetadataRule
import com.tencent.bkrepo.repository.pojo.schedule.Platform
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadConflictStrategy
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadRuleScope.PROJECT
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadRuleScope.USER
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleUpdateRequest
import com.tencent.bkrepo.repository.service.schedule.ScheduledDownloadRuleService
import com.tencent.bkrepo.repository.service.schedule.impl.ScheduledDownloadRuleServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource

@DisplayName("预约下载规则测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    ScheduledDownloadRuleDao::class,
    ScheduledDownloadRuleServiceImpl::class,
    ScheduledDownloadRuleServiceTest.MockPermissionManager::class,
    PrincipalManager::class,
    HttpAuthProperties::class,
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties", "classpath:center-ut.properties"])
class ScheduledDownloadRuleServiceTest @Autowired constructor(
    private val ruleDao: ScheduledDownloadRuleDao,
    private val ruleService: ScheduledDownloadRuleService,
) {
    @MockBean
    private lateinit var servicePermissionClient: ServicePermissionClient

    @MockBean
    private lateinit var externalPermissionClient: ServiceExternalPermissionClient

    @MockBean
    private lateinit var userClient: ServiceUserClient

    @MockBean
    private lateinit var projectService: ProjectService

    @MockBean
    private lateinit var repositoryService: RepositoryService

    @MockBean
    private lateinit var nodeService: NodeService

    @BeforeEach
    fun beforeEach() {
        ruleDao.remove(Query())
    }


    @Test
    fun `test create rule`() {
        // create project rule success
        val req = buildCreateReq(setOf("user1", "user2"))
        var rule = ruleService.create(req)
        assertEquals(2, rule.userIds!!.size)

        rule = ruleService.create(req.copy(scope = USER))
        assertTrue(rule.userIds!!.size == 1 && rule.userIds!!.first() == USER_ADMIN)

        rule = ruleService.create(req.copy(operator = USER_NORMAL, scope = USER))
        assertTrue(rule.userIds!!.size == 1 && rule.userIds!!.first() == USER_NORMAL)

        // check permission failed
        assertThrows<PermissionException> { ruleService.create(req.copy(operator = USER_NORMAL, scope = PROJECT)) }
        assertThrows<PermissionException> { ruleService.create(req.copy(operator = USER_NO_PERMISSION, scope = USER)) }

        // check param failed
        assertDoesNotThrow { ruleService.create(req.copy(cron = "0 0 2 1 * ?")) }
        assertDoesNotThrow { ruleService.create(req.copy(cron = "0 15 10 ? * 6L 2025")) }
        assertThrows<ErrorCodeException> { ruleService.create(req.copy(cron = "0 15 10 ? * 6L xxxx")) }
        assertThrows<ErrorCodeException> { ruleService.create(req.copy(cron = "xxx")) }
        assertThrows<ErrorCodeException> { ruleService.create(req.copy(fullPathRegex = "[a-z")) }
    }

    @Test
    fun `test remove rule`() {
        val req = buildCreateReq(setOf("user1", "user2"))
        val projectRule = ruleService.create(req)
        val userRule = ruleService.create(req.copy(operator = USER_NORMAL, scope = USER))

        // check permission failed
        assertThrows<PermissionException> { ruleService.remove(projectRule.id!!, USER_NORMAL) }
        assertThrows<PermissionException> { ruleService.remove(userRule.id!!, USER_ADMIN) }

        // remove success
        ruleService.remove(projectRule.id!!, USER_ADMIN)
        assertNull(ruleDao.findById(projectRule.id!!))
        ruleService.remove(userRule.id!!, USER_NORMAL)
        assertNull(ruleDao.findById(userRule.id!!))
    }

    @Test
    fun `test update rule`() {
        val req = buildCreateReq(setOf("user1", "user2"))
        val projectRule = ruleService.create(req.copy(metadataRules = setOf(MetadataRule("k", "v"))))
        val userRule = ruleService.create(req.copy(operator = USER_NORMAL, scope = USER))

        val updateReq = UserScheduledDownloadRuleUpdateRequest(id = projectRule.id!!, operator = USER_ADMIN)
        // update project userIds success
        var updatedRule = ruleService.update(updateReq.copy(id = projectRule.id!!, userIds = setOf("user3")))
        assertEquals(1, updatedRule.userIds!!.size)

        // update user rule userIds failed
        updatedRule = ruleService.update(
            updateReq.copy(id = userRule.id!!, userIds = setOf("user3"), operator = USER_NORMAL)
        )
        assertTrue(updatedRule.userIds!!.size == 1 && updatedRule.userIds!!.first() == USER_NORMAL)

        // update metadata
        updatedRule = ruleService.update(
            updateReq.copy(id = projectRule.id!!, metadataRules = setOf(MetadataRule("k2", "v2")))
        )
        assertEquals("k2", updatedRule.metadataRules!!.first().key)

        // update failed
        assertThrows<PermissionException> {
            ruleService.update(updateReq.copy(id = projectRule.id!!, operator = USER_NORMAL))
        }
        assertThrows<PermissionException> {
            ruleService.update(updateReq.copy(id = userRule.id!!, operator = USER_ADMIN))
        }
    }

    @Test
    fun `test query`() {
        val req = buildCreateReq(setOf(USER_NORMAL, "user2"))
        val metadataRule = MetadataRule("k", "v")
        ruleService.create(req)
        ruleService.create(req.copy(userIds = null, metadataRules = setOf(metadataRule), platform = Platform.MACOS))
        ruleService.create(req.copy(operator = USER_NORMAL, scope = USER, platform = Platform.All))

        // query project rules
        val queryReq = UserScheduledDownloadRuleQueryRequest(projectIds = setOf(UT_PROJECT_ID), operator = USER_ADMIN)
        assertEquals(2, ruleService.projectRules(queryReq).totalRecords)
        assertEquals(1, ruleService.projectRules(queryReq.copy(userIds = setOf(USER_NORMAL))).totalRecords)
        assertEquals(1, ruleService.projectRules(queryReq.copy(metadataRules = setOf(metadataRule))).totalRecords)

        // query failed
        assertThrows<PermissionException> { ruleService.projectRules(queryReq.copy(operator = USER_NORMAL)) }

        // query rules
        assertThrows<PermissionException> { ruleService.projectRules(queryReq.copy(operator = "user2")) }
        assertEquals(1, ruleService.rules(queryReq.copy(operator = USER_ADMIN)).totalRecords)

        var rules = ruleService.rules(queryReq.copy(operator = USER_NORMAL))
        assertEquals(3, rules.totalRecords)
        rules.records.forEach { assertEquals(USER_NORMAL, it.userIds!!.first()) }

        rules = ruleService.rules(queryReq.copy(operator = USER_NORMAL, platform = Platform.WINDOWS))
        assertEquals(2, rules.totalRecords)
    }

    @Test
    fun `test get rule`() {
        val req = buildCreateReq(setOf(USER_NORMAL, "user2"))
        val projectRule = ruleService.create(req)
        val userRule = ruleService.create(req.copy(operator = USER_NORMAL, scope = USER))

        // user get project rule
        var rule = ruleService.get(projectRule.id!!, USER_NORMAL)
        assertTrue(rule.userIds!!.size == 1 && rule.userIds!!.first() == USER_NORMAL)

        // user get user rule
        rule = ruleService.get(userRule.id!!, USER_NORMAL)
        assertTrue(rule.userIds!!.size == 1 && rule.userIds!!.first() == USER_NORMAL)

        // admin get project rule
        rule = ruleService.get(projectRule.id!!, USER_ADMIN)
        assertEquals(2, rule.userIds!!.size)

        // get failed
        assertThrows<ErrorCodeException> { ruleService.get("not exists", USER_NORMAL) }
        assertThrows<PermissionException> { ruleService.get(userRule.id!!, USER_ADMIN) }
    }

    private fun buildCreateReq(userIds: Set<String>) = UserScheduledDownloadRuleCreateRequest(
        userIds = userIds,
        projectId = UT_PROJECT_ID,
        repoNames = setOf(UT_REPO_NAME),
        fullPathRegex = "\\.jar$",
        metadataRules = setOf(MetadataRule("pid", "xxxx")),
        cron = "0 0 3 * * ?",
        downloadDir = "~/Download",
        conflictStrategy = ScheduledDownloadConflictStrategy.OVERWRITE,
        enabled = true,
        platform = Platform.WINDOWS,
        scope = PROJECT,
        operator = USER_ADMIN
    )

    class MockPermissionManager(
        projectService: ProjectService,
        repositoryService: RepositoryService,
        permissionResource: ServicePermissionClient,
        externalPermissionResource: ServiceExternalPermissionClient,
        userResource: ServiceUserClient,
        nodeService: NodeService,
        httpAuthProperties: HttpAuthProperties,
        principalManager: PrincipalManager
    ) : PermissionManager(
        projectService,
        repositoryService,
        permissionResource,
        externalPermissionResource,
        userResource,
        nodeService,
        httpAuthProperties,
        principalManager,
    ) {
        override fun checkProjectPermission(action: PermissionAction, projectId: String, userId: String) {
            val users = setOf(USER_ADMIN, USER_NORMAL)
            val noManagerPermission = action == MANAGE && userId != USER_ADMIN
            val noDownloadPermission = action == DOWNLOAD && userId !in users
            if (noManagerPermission || noDownloadPermission) {
                throw PermissionException()
            }
        }
    }

    companion object {
        private const val USER_ADMIN = "admin"
        private const val USER_NORMAL = "user"
        private const val USER_NO_PERMISSION = "no_permission"
    }
}
