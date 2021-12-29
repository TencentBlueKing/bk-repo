package com.tencent.bkrepo.helm.service

import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@DisplayName("chart上传/删除操作测试")
@SpringBootTest
class ChartManipulationServiceTest {
    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
    }

    @Test
    @DisplayName("chart上传")
    fun uploadTest() {
        val file = File("/XXXXX/test/helm-local/mysql-1.5.0.tgz")
        val mockFile = MockMultipartFile("chart", "mysql-1.5.0.tgz", ",multipart/form-data", file.inputStream())
        val perform =
            mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/test/test/charts")
                    .file(mockFile)
                    .header("Authorization", "Basic XXXXX=")
            )
        perform.andExpect { MockMvcResultMatchers.status().is4xxClientError }
        perform.andExpect { MockMvcResultMatchers.status().isOk }
        println(perform.andReturn().response.contentAsString)
    }

    @Test
    @DisplayName("删除版本")
    fun deleteVersionTest() {
        val perform =
            mockMvc.perform(
                MockMvcRequestBuilders.delete("/test/test//api/charts/mysql/1.5.0").header(
                    "Authorization",
                    "Basic XXXXXX="
                ).contentType(MediaType.APPLICATION_JSON)
            )
        perform.andExpect { MockMvcResultMatchers.status().is4xxClientError }
        perform.andExpect { MockMvcResultMatchers.status().isOk }
        println(perform.andReturn().response.contentAsString)
    }
}
