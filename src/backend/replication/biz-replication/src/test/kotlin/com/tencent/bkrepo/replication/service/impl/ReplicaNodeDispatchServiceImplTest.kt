package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.matcher.RuleMatcher
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.ReplicaNodeDispatchConfigDao
import com.tencent.bkrepo.replication.enums.DispatchRuleIndex
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.dispatch.ReplicaNodeDispatchConfigInfo
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.service.ClusterNodeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("分发节点调度规则匹配")
class ReplicaNodeDispatchServiceImplTest {

    private val replicaNodeDispatchConfigDao: ReplicaNodeDispatchConfigDao = mock()
    private val clusterNodeService: ClusterNodeService = mock()
    private lateinit var service: ReplicaNodeDispatchServiceImpl

    @BeforeEach
    fun setUp() {
        service = ReplicaNodeDispatchServiceImpl(
            replicaNodeDispatchConfigDao,
            clusterNodeService,
            ReplicationProperties()
        )
        val clusterInfo = mock<ClusterNodeInfo>()
        whenever(clusterInfo.url).thenReturn(CLUSTER_URL)
        whenever(clusterNodeService.getByClusterId(CLUSTER_ID)).thenReturn(clusterInfo)
    }

    @Test
    fun `buildValuesToMatch includes taskName`() {
        val values = service.buildValuesToMatch(taskDetail())
        assertEquals(CLUSTER_HOST, values[DispatchRuleIndex.RULE_WITH_HOST.value])
        assertEquals(PROJECT_ID, values[DispatchRuleIndex.RULE_WITH_PROJECT.value])
        assertEquals(TOTAL_BYTES, values[DispatchRuleIndex.RULE_WITH_SIZE.value])
        assertEquals(TASK_NAME, values[DispatchRuleIndex.RULE_WITH_TASK_NAME.value])
        assertEquals(REPO_NAME, values[DispatchRuleIndex.RULE_WITH_REPO.value])
    }

    @Test
    fun `taskName CONTAINS substring hits and misses`() {
        val values = service.buildValuesToMatch(taskDetail())
        val hit = Rule.QueryRule(
            DispatchRuleIndex.RULE_WITH_TASK_NAME.value,
            "special-task",
            OperationType.CONTAINS
        )
        assertTrue(RuleMatcher.match(hit, values))
        assertFalse(RuleMatcher.match(hit.copy(value = "other-task"), values))
    }

    @Test
    fun `taskName AND host projectId size`() {
        val values = service.buildValuesToMatch(taskDetail())
        val hit = Rule.NestedRule(
            mutableListOf(
                Rule.QueryRule(
                    DispatchRuleIndex.RULE_WITH_TASK_NAME.value, "special-task", OperationType.CONTAINS
                ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_HOST.value, CLUSTER_HOST, OperationType.EQ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_PROJECT.value, PROJECT_ID, OperationType.EQ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_SIZE.value, TOTAL_BYTES, OperationType.EQ)
            ),
            Rule.NestedRule.RelationType.AND
        )
        assertTrue(RuleMatcher.match(hit, values))
        val miss = Rule.NestedRule(
            mutableListOf(
                Rule.QueryRule(
                    DispatchRuleIndex.RULE_WITH_TASK_NAME.value, "special-task", OperationType.CONTAINS
                ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_PROJECT.value, "other-project", OperationType.EQ)
            ),
            Rule.NestedRule.RelationType.AND
        )
        assertFalse(RuleMatcher.match(miss, values))
    }

    @Test
    fun `taskName OR host`() {
        val values = service.buildValuesToMatch(taskDetail())
        val hitByName = Rule.NestedRule(
            mutableListOf(
                Rule.QueryRule(
                    DispatchRuleIndex.RULE_WITH_TASK_NAME.value, "special-task", OperationType.CONTAINS
                ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_HOST.value, "other.host", OperationType.EQ)
            ),
            Rule.NestedRule.RelationType.OR
        )
        assertTrue(RuleMatcher.match(hitByName, values))
        val miss = Rule.NestedRule(
            mutableListOf(
                Rule.QueryRule(
                    DispatchRuleIndex.RULE_WITH_TASK_NAME.value, "other-task", OperationType.CONTAINS
                ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_HOST.value, "other.host", OperationType.EQ)
            ),
            Rule.NestedRule.RelationType.OR
        )
        assertFalse(RuleMatcher.match(miss, values))
    }

    @Test
    fun `mostSpecific prefers host AND taskName over host IN`() {
        val hostOnly = config(
            "old",
            "http://xxxx:25903",
            Rule.QueryRule(
                DispatchRuleIndex.RULE_WITH_HOST.value,
                listOf("dev.a.b.com", "test.a.b.com", "prod.a.b.com"),
                OperationType.IN
            )
        )
        val hostAndName = config(
            "new",
            "http://yyyy:25903",
            Rule.NestedRule(
                mutableListOf(
                    Rule.QueryRule(
                        DispatchRuleIndex.RULE_WITH_HOST.value,
                        listOf("dev.a.b.com"),
                        OperationType.IN
                    ),
                    Rule.QueryRule(
                        DispatchRuleIndex.RULE_WITH_TASK_NAME.value, "p-aaaa", OperationType.CONTAINS
                    )
                ),
                Rule.NestedRule.RelationType.AND
            )
        )
        val picked = ReplicaNodeDispatchServiceImpl.mostSpecific(listOf(hostOnly, hostAndName))
        assertEquals(1, picked.size)
        assertEquals("new", picked[0].id)
    }

    @Test
    fun `host IN still matches when taskName missing p-aaaa`() {
        val hostRule = Rule.QueryRule(
            DispatchRuleIndex.RULE_WITH_HOST.value,
            listOf("dev.a.b.com", "test.a.b.com"),
            OperationType.IN
        )
        val nameRule = Rule.NestedRule(
            mutableListOf(
                Rule.QueryRule(
                    DispatchRuleIndex.RULE_WITH_HOST.value, listOf("dev.a.b.com"), OperationType.IN
                ),
                Rule.QueryRule(
                    DispatchRuleIndex.RULE_WITH_TASK_NAME.value, "p-aaaa", OperationType.CONTAINS
                )
            ),
            Rule.NestedRule.RelationType.AND
        )
        val withKeyword = mapOf(
            DispatchRuleIndex.RULE_WITH_HOST.value to "dev.a.b.com",
            DispatchRuleIndex.RULE_WITH_TASK_NAME.value to "p1/generic/p-aaaa-job"
        )
        val withoutKeyword = mapOf(
            DispatchRuleIndex.RULE_WITH_HOST.value to "dev.a.b.com",
            DispatchRuleIndex.RULE_WITH_TASK_NAME.value to "p1/generic/other-job"
        )
        assertTrue(RuleMatcher.match(hostRule, withKeyword))
        assertTrue(RuleMatcher.match(nameRule, withKeyword))
        assertTrue(RuleMatcher.match(hostRule, withoutKeyword))
        assertFalse(RuleMatcher.match(nameRule, withoutKeyword))
    }

    @Test
    fun `project only matches all repos, repo rule is more specific`() {
        val projectRule = Rule.NestedRule(
            mutableListOf(
                Rule.QueryRule(
                    DispatchRuleIndex.RULE_WITH_HOST.value, listOf("dev.a.b.com"), OperationType.IN
                ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_PROJECT.value, PROJECT_ID, OperationType.EQ)
            ),
            Rule.NestedRule.RelationType.AND
        )
        val repoRule = Rule.NestedRule(
            mutableListOf(
                Rule.QueryRule(
                    DispatchRuleIndex.RULE_WITH_HOST.value, listOf("dev.a.b.com"), OperationType.IN
                ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_PROJECT.value, PROJECT_ID, OperationType.EQ),
                Rule.QueryRule(DispatchRuleIndex.RULE_WITH_REPO.value, REPO_NAME, OperationType.EQ)
            ),
            Rule.NestedRule.RelationType.AND
        )
        val sameRepo = mapOf(
            DispatchRuleIndex.RULE_WITH_HOST.value to "dev.a.b.com",
            DispatchRuleIndex.RULE_WITH_PROJECT.value to PROJECT_ID,
            DispatchRuleIndex.RULE_WITH_REPO.value to REPO_NAME
        )
        val otherRepo = mapOf(
            DispatchRuleIndex.RULE_WITH_HOST.value to "dev.a.b.com",
            DispatchRuleIndex.RULE_WITH_PROJECT.value to PROJECT_ID,
            DispatchRuleIndex.RULE_WITH_REPO.value to "other-repo"
        )
        assertTrue(RuleMatcher.match(projectRule, sameRepo))
        assertTrue(RuleMatcher.match(repoRule, sameRepo))
        assertTrue(RuleMatcher.match(projectRule, otherRepo))
        assertFalse(RuleMatcher.match(repoRule, otherRepo))
        val picked = ReplicaNodeDispatchServiceImpl.mostSpecific(
            listOf(
                config("project", "http://xxxx:25903", projectRule),
                config("repo", "http://yyyy:25903", repoRule)
            )
        )
        assertEquals(1, picked.size)
        assertEquals("repo", picked[0].id)
    }

    private fun config(id: String, nodeUrl: String, rule: Rule): ReplicaNodeDispatchConfigInfo {
        return ReplicaNodeDispatchConfigInfo(id = id, nodeUrl = nodeUrl, rule = rule, enable = true)
    }

    private fun taskDetail(name: String = TASK_NAME): ReplicaTaskDetail {
        return ReplicaTaskDetail(
            task = ReplicaTaskInfo(
                id = "task-id",
                key = "task-key",
                name = name,
                projectId = PROJECT_ID,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                replicaType = ReplicaType.RUN_ONCE,
                setting = ReplicaSetting(),
                remoteClusters = setOf(ClusterNodeName(CLUSTER_ID, "remote")),
                executionTimes = 0,
                createdBy = "ut",
                createdDate = "2026-01-01T00:00:00",
                lastModifiedBy = "ut",
                lastModifiedDate = "2026-01-01T00:00:00",
                totalBytes = TOTAL_BYTES
            ),
            objects = listOf(
                ReplicaObjectInfo(
                    localRepoName = REPO_NAME,
                    remoteProjectId = null,
                    remoteRepoName = null,
                    repoType = RepositoryType.GENERIC,
                    packageConstraints = null,
                    pathConstraints = null
                )
            )
        )
    }

    companion object {
        private const val CLUSTER_ID = "cluster-id"
        private const val CLUSTER_URL = "https://remote.example.com"
        private const val CLUSTER_HOST = "remote.example.com"
        private const val PROJECT_ID = "p1"
        private const val REPO_NAME = "generic"
        private const val TASK_NAME = "p1/generic/special-task-001"
        private const val TOTAL_BYTES = 1024L
    }
}
