package com.tencent.bkrepo.npm.service

import com.tencent.bkrepo.npm.utils.GsonUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@DisplayName("npm历史测试")
class DataMigrationServiceTest {
    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
    }

    @Test
    @DisplayName("npm数据迁移增量测试")
    fun searchPackageInfoTest() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/test/npm-local/dataMigrationByPkgName").param("pkgName", "babel-core, underscore").accept(MediaType.APPLICATION_JSON_UTF8_VALUE)
        ).andExpect { MockMvcResultMatchers.status().isOk }
            .andDo(MockMvcResultHandlers.print()).andReturn()
        val resultMap = GsonUtils.gsonToMaps<Any>(result.response.contentAsString)!!
        Assertions.assertEquals(resultMap["successCount"], 2)
        Assertions.assertEquals(resultMap["errCount"], 0)
    }
}
