package com.tencent.bkrepo.auth

import com.tencent.bkrepo.auth.pojo.AddClusterRequest
import com.tencent.bkrepo.auth.pojo.UpdateClusterRequest
import com.tencent.bkrepo.auth.service.ClusterService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("集群测试")
class ClusterServiceTest {
    @Autowired
    private lateinit var clusterService: ClusterService

    private val clusterId = "clusterId"

    @BeforeEach
    fun setUp() {
        clusterService.listCluster().forEach {
            if (it.clusterId == clusterId) {
                clusterService.delete(clusterId)
            }
        }
    }

    @AfterEach
    fun teardown() {
        clusterService.listCluster().forEach {
            if (it.clusterId == clusterId) {
                clusterService.delete(clusterId)
            }
        }
    }

    @Test
    @DisplayName("添加集群测试")
    fun addClusterTest() {
        val clusterRequest = AddClusterRequest(clusterId, "clusterAddr", "cert", false)
        val addCluster = clusterService.addCluster(clusterRequest)
        Assertions.assertTrue(addCluster)
        assertThrows<ErrorCodeException> { clusterService.addCluster(clusterRequest) }
    }

    @Test
    @DisplayName("列出所有集群测试")
    fun listCluster() {
        val clusterRequest = AddClusterRequest(clusterId, "clusterAddr", "cert", false)
        clusterService.addCluster(clusterRequest)
        val listCluster = clusterService.listCluster()
        Assertions.assertTrue(listCluster.size == 1)
    }

    @Test
    @DisplayName("删除集群测试")
    fun deleteTest() {
        assertThrows<ErrorCodeException> { clusterService.delete(clusterId) }
        val clusterRequest = AddClusterRequest(clusterId, "clusterAddr", "cert", false)
        clusterService.addCluster(clusterRequest)
        val delete = clusterService.delete(clusterId)
        Assertions.assertTrue(delete)
    }

    @Test
    @DisplayName("更新集群")
    fun updateClusterTest() {
        val updateClusterRequest = UpdateClusterRequest("newClusterAddr", "newCert", true)
        assertThrows<ErrorCodeException> { clusterService.updateCluster(clusterId, updateClusterRequest) }
        val clusterRequest = AddClusterRequest(clusterId, "clusterAddr", "cert", false)
        clusterService.addCluster(clusterRequest)
        val update = clusterService.updateCluster(clusterId, updateClusterRequest)
        Assertions.assertTrue(update)
    }
}