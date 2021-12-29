package com.tencent.bkrepo.helm.service

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

@DisplayName("helm修复服务测试")
@SpringBootTest
class HelmFixToolServiceTest {

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
    }

    val projectId = "test"
    val repoName = "test"

    @Test
    @DisplayName("元属性修复")
    fun metaDataRegenerateTest() {
        val perform =
            mockMvc.perform(
                MockMvcRequestBuilders.post("/test/test/metaDate/regenerate").header(
                    "Authorization",
                    "Basic XXXXX="
                ).contentType(MediaType.APPLICATION_JSON)
            )
        perform.andExpect { MockMvcResultMatchers.status().is4xxClientError }
        perform.andExpect { MockMvcResultMatchers.status().isOk }
    }
}
