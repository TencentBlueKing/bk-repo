package com.tencent.bkrepo.replication.service.impl.tracking

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.replicator.standalone.FederationReplicator
import com.tencent.bkrepo.replication.service.impl.FederationRepositoryServiceTestBase.Companion.TEST_USER
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@DisplayName("文件传输处理器测试")
@ExtendWith(MockitoExtension::class)
class FileTransferProcessorTest {

    @Mock
    private lateinit var replicaContextBuilder: ReplicaContextBuilder

    private lateinit var fileTransferProcessor: FileTransferProcessor

    private val testTaskKey = "test-task-key"
    private val testRemoteClusterId = "test-remote-cluster-id"
    private val testProjectId = "test-project-id"
    private val testLocalRepoName = "test-local-repo"
    private val testRemoteProjectId = "test-remote-project-id"
    private val testRemoteRepoName = "test-remote-repo"
    private val testNodePath = "/test/path"
    private val testNodeId = "test-node-id"

    @BeforeEach
    fun setUp() {
        // Mock SpringContextUtils companion object
        mockkObject(SpringContextUtils.Companion)
        fileTransferProcessor = FileTransferProcessor(
            replicaContextBuilder = replicaContextBuilder
        )
    }

    @AfterEach
    fun tearDown() {
        // Unmock SpringContextUtils after each test
        unmockkObject(SpringContextUtils.Companion)
    }

    @Test
    fun `test process - should return true when FederationReplicator succeeds`() {
        // 准备测试数据
        val trackingRecord = createTrackingRecord()
        val nodeInfo = createNodeInfo()
        val clusterNodeInfo = createClusterNodeInfo()

        val replicaContext = mock<ReplicaContext>()
        val federationReplicator = mockk<FederationReplicator>()

        // Mock依赖方法
        whenever(replicaContextBuilder.build(trackingRecord, clusterNodeInfo)).thenReturn(replicaContext)
        every { SpringContextUtils.getBean<FederationReplicator>() } returns federationReplicator
        every { federationReplicator.pushFileToFederatedClusterPublic(replicaContext, nodeInfo) } returns true

        // 执行处理
        val result = fileTransferProcessor.process(trackingRecord, nodeInfo, clusterNodeInfo)

        // 验证结果
        assertTrue(result)
    }

    @Test
    fun `test process - should return false when FederationReplicator fails`() {
        val trackingRecord = createTrackingRecord()
        val nodeInfo = createNodeInfo()
        val clusterNodeInfo = createClusterNodeInfo()

        val replicaContext = mock<ReplicaContext>()
        val federationReplicator = mockk<FederationReplicator>()

        whenever(replicaContextBuilder.build(trackingRecord, clusterNodeInfo)).thenReturn(replicaContext)
        every { SpringContextUtils.getBean<FederationReplicator>() } returns federationReplicator
        every { federationReplicator.pushFileToFederatedClusterPublic(replicaContext, nodeInfo) } returns false

        val result = fileTransferProcessor.process(trackingRecord, nodeInfo, clusterNodeInfo)

        assertFalse(result)
    }

    @Test
    fun `test process - should throw exception when FederationReplicator throws exception`() {
        val trackingRecord = createTrackingRecord()
        val nodeInfo = createNodeInfo()
        val clusterNodeInfo = createClusterNodeInfo()

        val replicaContext = mock<ReplicaContext>()
        val federationReplicator = mockk<FederationReplicator>()

        whenever(replicaContextBuilder.build(trackingRecord, clusterNodeInfo)).thenReturn(replicaContext)
        every { SpringContextUtils.getBean<FederationReplicator>() } returns federationReplicator
        every {
            federationReplicator.pushFileToFederatedClusterPublic(replicaContext, nodeInfo)
        } throws RuntimeException("Network error")

        // 验证异常被抛出
        val exception = org.junit.jupiter.api.assertThrows<RuntimeException> {
            fileTransferProcessor.process(trackingRecord, nodeInfo, clusterNodeInfo)
        }

        assertTrue(exception.message!!.contains("Network error"))
    }

    @Test
    fun `test process - should use correct record and cluster info`() {
        val trackingRecord = createTrackingRecord(
            taskKey = "custom-task",
            nodePath = "/custom/path"
        )
        val nodeInfo = createNodeInfo()
        val clusterNodeInfo = createClusterNodeInfo()

        val replicaContext = mock<ReplicaContext>()
        val federationReplicator = mockk<FederationReplicator>()

        whenever(replicaContextBuilder.build(trackingRecord, clusterNodeInfo)).thenReturn(replicaContext)
        every { SpringContextUtils.getBean<FederationReplicator>() } returns federationReplicator
        every { federationReplicator.pushFileToFederatedClusterPublic(replicaContext, nodeInfo) } returns true

        val result = fileTransferProcessor.process(trackingRecord, nodeInfo, clusterNodeInfo)

        assertTrue(result)
        // 验证使用了正确的参数
        org.mockito.kotlin.verify(replicaContextBuilder).build(trackingRecord, clusterNodeInfo)
        verify { federationReplicator.pushFileToFederatedClusterPublic(replicaContext, nodeInfo) }
    }

    @Test
    fun `test process - should handle different nodeInfo`() {
        val trackingRecord = createTrackingRecord()
        val customNodeInfo = NodeInfo(
            projectId = "custom-project",
            repoName = "custom-repo",
            path = "/custom/",
            folder = false,
            name = "custom-file",
            size = 2048L,
            sha256 = "custom-sha256",
            md5 = "custom-md5",
            fullPath = "/custom/path",
            createdBy = "xx",
            createdDate = "xxx",
            lastModifiedBy = "xxx",
            lastModifiedDate = "xxx"
        )
        val clusterNodeInfo = createClusterNodeInfo()

        val replicaContext = mock<ReplicaContext>()
        val federationReplicator = mockk<FederationReplicator>()

        whenever(replicaContextBuilder.build(trackingRecord, clusterNodeInfo)).thenReturn(replicaContext)
        every { SpringContextUtils.getBean<FederationReplicator>() } returns federationReplicator
        every { federationReplicator.pushFileToFederatedClusterPublic(replicaContext, customNodeInfo) } returns true

        val result = fileTransferProcessor.process(trackingRecord, customNodeInfo, clusterNodeInfo)

        assertTrue(result)
        verify { federationReplicator.pushFileToFederatedClusterPublic(replicaContext, customNodeInfo) }
    }

    // ========== 辅助方法 ==========

    private fun createTrackingRecord(
        taskKey: String = testTaskKey,
        remoteClusterId: String = testRemoteClusterId,
        projectId: String = testProjectId,
        localRepoName: String = testLocalRepoName,
        remoteProjectId: String = testRemoteProjectId,
        remoteRepoName: String = testRemoteRepoName,
        nodePath: String = testNodePath,
        nodeId: String = testNodeId
    ): TFederationMetadataTracking {
        return TFederationMetadataTracking(
            taskKey = taskKey,
            remoteClusterId = remoteClusterId,
            projectId = projectId,
            localRepoName = localRepoName,
            remoteProjectId = remoteProjectId,
            remoteRepoName = remoteRepoName,
            nodePath = nodePath,
            nodeId = nodeId,
            createdDate = LocalDateTime.now()
        )
    }

    private fun createNodeInfo(): NodeInfo {
        return NodeInfo(
            projectId = testProjectId,
            repoName = testLocalRepoName,
            path = "/test/",
            folder = false,
            name = "test-file",
            size = 1024L,
            sha256 = "test-sha256",
            md5 = "test-md5",
            fullPath = testNodePath,
            createdBy = "xx",
            createdDate = "xxx",
            lastModifiedBy = "xxx",
            lastModifiedDate = "xxx"
        )
    }

    private fun createClusterNodeInfo(): ClusterNodeInfo {
        return ClusterNodeInfo(
            id = testRemoteClusterId,
            name = "Test Cluster",
            url = "http://test-cluster.com",
            username = "admin",
            password = "xxxx",
            certificate = null,
            accessKey = null,
            secretKey = null,
            appId = null,
            createdBy = TEST_USER,
            createdDate = LocalDateTime.now().toString(),
            lastModifiedBy = TEST_USER,
            lastModifiedDate = LocalDateTime.now().toString(),
            detectType = null,
            lastReportTime = null,
            status = ClusterNodeStatus.HEALTHY,
            errorReason = null,
            type = ClusterNodeType.STANDALONE
        )
    }
}
