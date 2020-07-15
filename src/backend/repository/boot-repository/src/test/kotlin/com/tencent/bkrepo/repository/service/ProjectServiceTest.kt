package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * ProjectServiceTest
 *
 */
@DisplayName("项目服务测试")
@Disabled
@SpringBootTest(properties = ["auth.enabled=false"])
internal class ProjectServiceTest @Autowired constructor(
    private val projectService: ProjectService
) {

    private val operator = "system"

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {

    }

    @Test
    fun create() {
        val request = ProjectCreateRequest("unit-test", "测试项目", "单元测试项目", operator)
        projectService.create(request)
    }

}
