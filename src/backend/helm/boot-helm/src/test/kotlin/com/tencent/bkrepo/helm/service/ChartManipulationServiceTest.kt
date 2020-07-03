package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@DisplayName("helm仓库上传操作测试")
@SpringBootTest
class ChartManipulationServiceTest {
    @Autowired
    private lateinit var chartManipulationService: ChartManipulationService

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp(){
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
    }

    @AfterEach
    fun tearDown(){}

    @Test
    @DisplayName("测试tgz包的全路径")
    fun chartFileFullPathTest(){
        val chartMap = mapOf("name" to "bk-redis", "version" to "0.1.1")
        val chartFileFullPath = chartManipulationService.getChartFileFullPath(chartMap)
        Assertions.assertEquals(chartFileFullPath,"/bk-redis-0.1.1.tgz")
        Assertions.assertNotEquals(chartFileFullPath,"/bk-redis-0.1.1.tgz.prov")
    }

    @Test
    @DisplayName("chart信息解析测试")
    fun chartInfoTest(){
        val artifactInfo = HelmArtifactInfo("test","helm-local","/bk-redis/0.1.1")
        val chartFileFullPath = chartManipulationService.getChartInfo(artifactInfo)
        Assertions.assertEquals(chartFileFullPath.first,"bk-redis")
        Assertions.assertEquals(chartFileFullPath.second,"0.1.1")
    }

}