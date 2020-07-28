package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest

@DisplayName("项目服务测试")
@DataMongoTest
class ProjectServiceTest @Autowired constructor(
    private val projectService: ProjectService
): ServiceBaseTest() {

    private val operator = "system"

    @BeforeEach
    fun beforeEach() {
        initMock()
    }

    @Test
    fun create() {
        val request = ProjectCreateRequest("unit-test", "测试项目", "单元测试项目", operator)
        projectService.create(request)
    }
}
