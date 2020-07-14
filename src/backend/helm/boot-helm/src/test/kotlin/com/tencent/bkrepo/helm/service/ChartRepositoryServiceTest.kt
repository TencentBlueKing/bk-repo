package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@DisplayName("helm仓库获取tgz包测试")
@SpringBootTest
class ChartRepositoryServiceTest {
    @Autowired
    private lateinit var chartRepositoryService: ChartRepositoryService

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    @DisplayName("index.yaml文件刷新测试")
    fun getIndexYamlTest() {
        val perform =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/test/helm-local/index.yaml").header(
                    "Authorization",
                    "Basic eHdoeToxMjM0NTY="
                ).contentType(MediaType.APPLICATION_JSON_UTF8)
            )
        perform.andExpect { MockMvcResultMatchers.status().is4xxClientError }
        perform.andExpect { MockMvcResultMatchers.status().isOk }
        val contentLength = perform.andReturn().response.contentLength
        println("****************$contentLength")
    }

    @Test
    @DisplayName("自定义查询测试")
    fun queryNodeTest() {
        val artifactInfo = HelmArtifactInfo("test", "helm-local", "")
        val queryNodeList = chartRepositoryService.queryNodeList(artifactInfo, false)
        Assertions.assertEquals(queryNodeList.size, 5)
    }

    @Test
    @DisplayName("index修改测试")
    fun addIndexEntriesTest() {
        val indexEntity = chartRepositoryService.initIndexEntity()
        val chartInfoMap: MutableMap<String, Any> = mutableMapOf("name" to "bk-redis", "version" to "0.2.1")
        chartRepositoryService.addIndexEntries(indexEntity, chartInfoMap)
        Assertions.assertEquals(indexEntity.entriesSize(), 1)
        chartRepositoryService.addIndexEntries(indexEntity, chartInfoMap)
        println("*******" + indexEntity.entries)
        Assertions.assertEquals(indexEntity.entriesSize(), 1)
    }
}