package com.tencent.bkrepo.replication.service.impl.tracking

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.impl.FederationRepositoryServiceTestBase.Companion.TEST_CLUSTER_ID_1
import com.tencent.bkrepo.replication.service.impl.FederationRepositoryServiceTestBase.Companion.TEST_USER
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@DisplayName("文件传输验证器测试")
@ExtendWith(MockitoExtension::class)
class FileTransferValidatorTest {

    @Mock
    private lateinit var localDataManager: LocalDataManager

    @Mock
    private lateinit var clusterNodeService: ClusterNodeService

    private lateinit var fileTransferValidator: FileTransferValidator

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
        fileTransferValidator = FileTransferValidator(
            localDataManager = localDataManager,
            clusterNodeService = clusterNodeService
        )
    }

    @Test
    fun `test validate - should return valid result when node and cluster exist`() {
        // 准备测试数据
        val trackingRecord = createTrackingRecord()
        val nodeInfo = createNodeInfo()
        val clusterNodeInfo = createClusterNodeInfo()

        // Mock依赖方法
        whenever(localDataManager.findNodeById(testProjectId, testNodeId)).thenReturn(nodeInfo)
        whenever(clusterNodeService.getByClusterId(testRemoteClusterId)).thenReturn(clusterNodeInfo)

        // 执行验证
        val result = fileTransferValidator.validate(trackingRecord)

        // 验证结果
        assertTrue(result.isValid)
        assertNotNull(result.nodeInfo)
        assertEquals(nodeInfo, result.nodeInfo)
        assertNotNull(result.clusterNodeInfo)
        assertEquals(clusterNodeInfo, result.clusterNodeInfo)
        assertNull(result.errorMessage)
    }

    @Test
    fun `test validate - should return invalid result when node not found`() {
        val trackingRecord = createTrackingRecord()

        // Mock节点不存在
        whenever(localDataManager.findNodeById(testProjectId, testNodeId)).thenReturn(null)

        // 执行验证
        val result = fileTransferValidator.validate(trackingRecord)

        // 验证结果
        assertFalse(result.isValid)
        assertNull(result.nodeInfo)
        assertNull(result.clusterNodeInfo)
        assertNotNull(result.errorMessage)
        assertEquals("Node not found: $testNodePath", result.errorMessage)
    }

    @Test
    fun `test validate - should return invalid result when cluster not found`() {
        val trackingRecord = createTrackingRecord()
        val nodeInfo = createNodeInfo()

        // Mock节点存在，但集群不存在
        whenever(localDataManager.findNodeById(testProjectId, testNodeId)).thenReturn(nodeInfo)
        whenever(clusterNodeService.getByClusterId(testRemoteClusterId)).thenReturn(null)

        // 执行验证
        val result = fileTransferValidator.validate(trackingRecord)

        // 验证结果
        assertFalse(result.isValid)
        assertNull(result.nodeInfo) // 节点信息仍然返回
        assertNull(result.clusterNodeInfo)
        assertNotNull(result.errorMessage)
        assertEquals("Cluster not found: $testRemoteClusterId", result.errorMessage)
    }

    @Test
    fun `test validate - should use correct nodePath in error message when node not found`() {
        val trackingRecord = createTrackingRecord(nodePath = "/custom/error/path")

        whenever(localDataManager.findNodeById(testProjectId, testNodeId)).thenReturn(null)

        val result = fileTransferValidator.validate(trackingRecord)

        assertFalse(result.isValid)
        assertEquals("Node not found: /custom/error/path", result.errorMessage)
    }

    @Test
    fun `test validate - should use correct clusterId in error message when cluster not found`() {
        val trackingRecord = createTrackingRecord(remoteClusterId = "custom-cluster-id")
        val nodeInfo = createNodeInfo()

        whenever(localDataManager.findNodeById(testProjectId, testNodeId)).thenReturn(nodeInfo)
        whenever(clusterNodeService.getByClusterId("custom-cluster-id")).thenReturn(null)

        val result = fileTransferValidator.validate(trackingRecord)

        assertFalse(result.isValid)
        assertEquals("Cluster not found: custom-cluster-id", result.errorMessage)
    }

    @Test
    fun `test validate - should validate with different projectId and nodeId`() {
        val trackingRecord = createTrackingRecord(
            projectId = "different-project",
            nodeId = "different-node-id"
        )
        val nodeInfo = createNodeInfo()
        val clusterNodeInfo = createClusterNodeInfo()

        whenever(localDataManager.findNodeById("different-project", "different-node-id")).thenReturn(nodeInfo)
        whenever(clusterNodeService.getByClusterId(testRemoteClusterId)).thenReturn(clusterNodeInfo)

        val result = fileTransferValidator.validate(trackingRecord)

        assertTrue(result.isValid)
        assertNotNull(result.nodeInfo)
        assertNotNull(result.clusterNodeInfo)
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
            path = "/test",
            folder = false,
            name = "test-file",
            size = 1024L,
            sha256 = "test-sha256",
            md5 = "test-md5",
            fullPath = testNodePath,
            createdBy = "test",
            createdDate = "xxxx",
            lastModifiedDate = "xxx",
            lastModifiedBy = "xxxx"
        )
    }

    fun createClusterNodeInfo(
        id: String = TEST_CLUSTER_ID_1,
        name: String = "test-cluster",
        url: String = "http://test-cluster.com"
    ): ClusterNodeInfo {
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
