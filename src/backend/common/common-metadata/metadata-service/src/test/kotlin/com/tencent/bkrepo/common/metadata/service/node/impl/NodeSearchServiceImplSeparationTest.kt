package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.search.node.NodeQueryContext
import com.tencent.bkrepo.common.metadata.search.node.NodeQueryInterpreter
import com.tencent.bkrepo.common.metadata.service.metadata.impl.MetadataLabelCacheService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.separation.SeparationDataService
import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.util.SecurityUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.query.Query

@ExtendWith(MockitoExtension::class)
@DisplayName("NodeSearchServiceImpl 冷热联合查询")
class NodeSearchServiceImplSeparationTest {

    @Mock
    private lateinit var nodeDao: NodeDao

    @Mock
    private lateinit var nodeQueryInterpreter: NodeQueryInterpreter

    @Mock
    private lateinit var repositoryService: RepositoryService

    @Mock
    private lateinit var repositoryProperties: RepositoryProperties

    @Mock
    private lateinit var metadataLabelCacheService: MetadataLabelCacheService

    @Mock
    private lateinit var separationDataService: SeparationDataService

    @Mock
    private lateinit var dataSeparationConfig: DataSeparationConfig

    @InjectMocks
    private lateinit var service: NodeSearchServiceImpl

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurityUtils)
    }

    @Test
    @DisplayName("specialSeparateRepos 为空时不查冷表")
    fun search_skipsCold_whenNoSeparateRepos() {
        whenever(dataSeparationConfig.specialSeparateRepos).thenReturn(mutableListOf())
        whenever(repositoryProperties.slowLogTimeThreshold).thenReturn(Long.MAX_VALUE)
        val ctx = nodeContext("p", "r")
        whenever(nodeQueryInterpreter.interpret(any())).thenReturn(ctx)
        whenever(metadataLabelCacheService.listAll("p")).thenReturn(emptyList())
        whenever(nodeDao.find(any<Query>(), eq(MutableMap::class.java))).thenReturn(
            mutableListOf(mutableMapOf(TNode::projectId.name to "p")),
        )
        whenever(nodeDao.count(any())).thenReturn(0L)

        service.search(queryModel())

        verify(separationDataService, never()).countColdNodes(any())
        verify(separationDataService, never()).searchColdNodes(any(), any(), any())
    }

    @Test
    @DisplayName("separationQueryPlatformIds 非空且平台不匹配时不查冷表")
    fun search_skipsCold_whenPlatformNotAllowed() {
        mockkObject(SecurityUtils)
        every { SecurityUtils.getPlatformId() } returns "plat-b"
        whenever(dataSeparationConfig.specialSeparateRepos).thenReturn(mutableListOf("p/r"))
        whenever(dataSeparationConfig.separationQueryPlatformIds).thenReturn(mutableListOf("plat-a"))
        whenever(repositoryProperties.slowLogTimeThreshold).thenReturn(Long.MAX_VALUE)
        val ctx = nodeContext("p", "r")
        whenever(nodeQueryInterpreter.interpret(any())).thenReturn(ctx)
        whenever(metadataLabelCacheService.listAll("p")).thenReturn(emptyList())
        whenever(nodeDao.find(any<Query>(), eq(MutableMap::class.java))).thenReturn(
            mutableListOf(mutableMapOf(TNode::projectId.name to "p")),
        )
        whenever(nodeDao.count(any())).thenReturn(0L)

        service.search(queryModel())

        verify(separationDataService, never()).countColdNodes(any())
    }

    @Test
    @DisplayName("平台与仓库匹配时合并冷表 count 与分页补充")
    fun search_mergesCold_whenPlatformAndRepoAllowed() {
        mockkObject(SecurityUtils)
        every { SecurityUtils.getPlatformId() } returns "plat-a"
        whenever(dataSeparationConfig.specialSeparateRepos).thenReturn(mutableListOf("p/r"))
        whenever(dataSeparationConfig.separationQueryPlatformIds).thenReturn(mutableListOf("plat-a"))
        whenever(repositoryProperties.slowLogTimeThreshold).thenReturn(Long.MAX_VALUE)
        val ctx = nodeContext("p", "r")
        whenever(nodeQueryInterpreter.interpret(any())).thenReturn(ctx)
        whenever(metadataLabelCacheService.listAll("p")).thenReturn(emptyList())
        whenever(nodeDao.find(any<Query>(), eq(MutableMap::class.java))).thenReturn(
            mutableListOf(mutableMapOf(TNode::projectId.name to "p")),
        )
        whenever(nodeDao.count(any())).thenReturn(2L)
        whenever(separationDataService.countColdNodes(any())).thenReturn(5L)
        whenever(separationDataService.searchColdNodes(any(), any(), any())).thenReturn(
            listOf(mutableMapOf("fullPath" to "/c.txt")),
        )

        val page = service.search(queryModel())
        assertEquals(7L, page.totalRecords)
        assertEquals(2, page.records.size)

        verify(separationDataService).countColdNodes(any())
        verify(separationDataService).searchColdNodes(any(), any(), any())
    }

    private fun queryModel(): QueryModel = QueryModel(
        page = PageLimit(pageNumber = 1, pageSize = 10),
        sort = null,
        select = null,
        rule = Rule.NestedRule(
            mutableListOf(
                Rule.QueryRule(TNode::projectId.name, "p"),
                Rule.QueryRule(TNode::repoName.name, "r"),
            ),
            Rule.NestedRule.RelationType.AND,
        ),
    )

    private fun nodeContext(projectId: String, repoName: String): NodeQueryContext {
        val q = Query()
        q.limit(10)
        q.skip(0)
        val qm = QueryModel(
            PageLimit(1, 10),
            null,
            null,
            Rule.NestedRule(
                mutableListOf(
                    Rule.QueryRule(TNode::projectId.name, projectId),
                    Rule.QueryRule(TNode::repoName.name, repoName),
                ),
                Rule.NestedRule.RelationType.AND,
            ),
        )
        val interpreter = org.mockito.Mockito.mock(MongoQueryInterpreter::class.java)
        return NodeQueryContext(qm, false, q, interpreter)
    }
}
