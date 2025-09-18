package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryUpdateRequest
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

/**
 * 联邦仓库服务测试基类
 * 提供通用的测试数据构建方法和配置
 */
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
abstract class FederationRepositoryServiceTestBase {

    companion object {
        const val TEST_PROJECT_ID = "test-project"
        const val TEST_REPO_NAME = "test-repo"
        const val TEST_FEDERATION_ID = "test-federation-id"
        const val TEST_FEDERATION_NAME = "test-federation"
        const val TEST_CLUSTER_ID_1 = "cluster-1"
        const val TEST_CLUSTER_ID_2 = "cluster-2"
        const val TEST_CLUSTER_ID_3 = "cluster-3"
        const val TEST_USER = "test-user"
    }

    /**
     * 创建测试用的集群节点信息
     */
    protected fun createTestClusterNodeInfo(
        id: String = TEST_CLUSTER_ID_1,
        name: String = "test-cluster",
        url: String = "http://test-cluster.com"
    ): ClusterNodeInfo {
        return ClusterNodeInfo(
            id = id,
            name = name,
            url = url,
            username = "admin",
            password = "password",
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

    /**
     * 创建测试用的联邦集群
     */
    protected fun createTestFederatedCluster(
        projectId: String = TEST_PROJECT_ID,
        repoName: String = TEST_REPO_NAME,
        clusterId: String = TEST_CLUSTER_ID_1,
        enabled: Boolean = true,
        taskId: String? = "task-id"
    ): FederatedCluster {
        return FederatedCluster(
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            enabled = enabled,
            taskId = taskId
        )
    }

    /**
     * 创建测试用的联邦仓库实体
     */
    protected fun createTestTFederatedRepository(
        projectId: String = TEST_PROJECT_ID,
        repoName: String = TEST_REPO_NAME,
        clusterId: String = TEST_CLUSTER_ID_1,
        federationId: String = TEST_FEDERATION_ID,
        name: String = TEST_FEDERATION_NAME,
        federatedClusters: List<FederatedCluster> = listOf(createTestFederatedCluster()),
        isFullSyncing: Boolean = false
    ): TFederatedRepository {
        return TFederatedRepository(
            createdBy = TEST_USER,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = TEST_USER,
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            federationId = federationId,
            name = name,
            federatedClusters = federatedClusters,
            isFullSyncing = isFullSyncing
        )
    }

    /**
     * 创建测试用的联邦仓库创建请求
     */
    protected fun createTestFederatedRepositoryCreateRequest(
        name: String = TEST_FEDERATION_NAME,
        projectId: String = TEST_PROJECT_ID,
        repoName: String = TEST_REPO_NAME,
        clusterId: String = TEST_CLUSTER_ID_1,
        federatedClusters: List<FederatedCluster> = listOf(createTestFederatedCluster())
    ): FederatedRepositoryCreateRequest {
        return FederatedRepositoryCreateRequest(
            name = name,
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            federatedClusters = federatedClusters
        )
    }

    /**
     * 创建测试用的联邦仓库更新请求
     */
    protected fun createTestFederatedRepositoryUpdateRequest(
        projectId: String = TEST_PROJECT_ID,
        repoName: String = TEST_REPO_NAME,
        federationId: String = TEST_FEDERATION_ID,
        federatedClusters: List<FederatedCluster> = listOf(createTestFederatedCluster())
    ): FederatedRepositoryUpdateRequest {
        return FederatedRepositoryUpdateRequest(
            projectId = projectId,
            repoName = repoName,
            federationId = federationId,
            federatedClusters = federatedClusters
        )
    }

    /**
     * 创建测试用的联邦仓库信息
     */
    protected fun createTestFederatedRepositoryInfo(
        name: String = TEST_FEDERATION_NAME,
        federationId: String = TEST_FEDERATION_ID,
        projectId: String = TEST_PROJECT_ID,
        repoName: String = TEST_REPO_NAME,
        clusterId: String = TEST_CLUSTER_ID_1,
        federatedClusters: List<FederatedCluster> = listOf(createTestFederatedCluster()),
        isFullSyncing: Boolean = false
    ): FederatedRepositoryInfo {
        return FederatedRepositoryInfo(
            name = name,
            federationId = federationId,
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            federatedClusters = federatedClusters,
            isFullSyncing = isFullSyncing,
            lastFullSyncStartTime = null,
            lastFullSyncEndTime = null,
        )
    }

    /**
     * 创建多个测试集群
     */
    protected fun createMultipleTestClusters(count: Int = 3): List<ClusterNodeInfo> {
        return (1..count).map { index ->
            createTestClusterNodeInfo(
                id = "cluster-$index",
                name = "cluster-$index",
                url = "http://cluster$index.com"
            )
        }
    }

    /**
     * 创建多个测试联邦集群
     */
    protected fun createMultipleTestFederatedClusters(
        clusterIds: List<String> = listOf(TEST_CLUSTER_ID_1, TEST_CLUSTER_ID_2, TEST_CLUSTER_ID_3),
        projectId: String = TEST_PROJECT_ID,
        repoName: String = TEST_REPO_NAME
    ): List<FederatedCluster> {
        return clusterIds.mapIndexed { index, clusterId ->
            createTestFederatedCluster(
                projectId = projectId,
                repoName = repoName,
                clusterId = clusterId,
                taskId = "task-${index + 1}"
            )
        }
    }

    /**
     * 验证联邦仓库信息的基本字段
     */
    protected fun assertFederatedRepositoryInfo(
        actual: FederatedRepositoryInfo,
        expected: FederatedRepositoryInfo
    ) {
        assert(actual.name == expected.name) { "Federation name mismatch" }
        assert(actual.federationId == expected.federationId) { "Federation ID mismatch" }
        assert(actual.projectId == expected.projectId) { "Project ID mismatch" }
        assert(actual.repoName == expected.repoName) { "Repository name mismatch" }
        assert(actual.clusterId == expected.clusterId) { "Cluster ID mismatch" }
        assert(actual.federatedClusters.size == expected.federatedClusters.size) { "Federated clusters size mismatch" }
    }

    /**
     * 验证联邦集群信息
     */
    protected fun assertFederatedCluster(
        actual: FederatedCluster,
        expected: FederatedCluster
    ) {
        assert(actual.projectId == expected.projectId) { "Project ID mismatch" }
        assert(actual.repoName == expected.repoName) { "Repository name mismatch" }
        assert(actual.clusterId == expected.clusterId) { "Cluster ID mismatch" }
        assert(actual.enabled == expected.enabled) { "Enabled status mismatch" }
    }
}