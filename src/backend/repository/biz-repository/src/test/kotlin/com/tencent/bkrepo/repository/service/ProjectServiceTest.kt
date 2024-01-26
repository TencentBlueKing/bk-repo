/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.UT_PROJECT_DESC
import com.tencent.bkrepo.repository.UT_PROJECT_DISPLAY
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.dao.ProjectDao
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectUpdateRequest
import com.tencent.bkrepo.repository.service.repo.ProjectService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.query.Query

@DisplayName("项目服务测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectServiceTest @Autowired constructor(
    private val projectService: ProjectService,
    private val projectDao: ProjectDao
) : ServiceBaseTest() {

    @BeforeAll
    fun beforeAll() {
        initMock()
    }

    @BeforeEach
    fun beforeEach() {
        removeAllProject()
    }

    @Test
    @DisplayName("测试创建项目")
    fun `test create project`() {
        val request = ProjectCreateRequest(UT_PROJECT_ID, UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)
    }

    @Test
    @DisplayName("测试更新项目")
    fun `test update project`() {
        // create project
        val metadata = ProjectMetadata(key = "k", value = "v")
        val request = ProjectCreateRequest(
            UT_PROJECT_ID,
            UT_PROJECT_DISPLAY,
            UT_PROJECT_DESC,
            true,
            UT_USER,
            listOf(metadata)
        )
        val projectInfo = projectService.createProject(request)
        Assertions.assertEquals(UT_PROJECT_ID, projectInfo.name)
        Assertions.assertEquals(1, projectInfo.metadata.size)

        // update project
        val newDisplayName = "$UT_PROJECT_DISPLAY new"
        val newDes = "$UT_PROJECT_DESC new"
        val newMetadata = listOf(ProjectMetadata(key = "newKey", value = "newVal"))
        val updateRequest = ProjectUpdateRequest(
            displayName = newDisplayName,
            description = newDes,
            metadata = newMetadata,
        )
        projectService.updateProject(UT_PROJECT_ID, updateRequest)
        val newProject = projectService.getProjectInfo(UT_PROJECT_ID)
        Assertions.assertNotNull(newProject)
        Assertions.assertEquals(newDisplayName, newProject!!.displayName)
        Assertions.assertEquals(newDes, newProject.description)
        Assertions.assertEquals(newMetadata.size, newProject.metadata.size)
        for (i in newMetadata.indices) {
            Assertions.assertEquals(newMetadata[i], newProject.metadata[i])
        }
    }

    @Test
    @DisplayName("测试创建同名项目")
    fun `should throw exception when project exists`() {
        val request = ProjectCreateRequest(UT_PROJECT_ID, UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)
        assertThrows<ErrorCodeException> { projectService.createProject(request) }
    }

    @Test
    @DisplayName("测试非法项目名称")
    fun `should throw exception with illegal name`() {
        var request = ProjectCreateRequest("1", UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        assertThrows<ErrorCodeException> { projectService.createProject(request) }

        request = ProjectCreateRequest("11", UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        assertThrows<ErrorCodeException> { projectService.createProject(request) }

        request = ProjectCreateRequest("a".repeat(33), UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        assertThrows<ErrorCodeException> { projectService.createProject(request) }

        request = ProjectCreateRequest("test_1", UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)

        request = ProjectCreateRequest("test-1", UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)

        request = ProjectCreateRequest("a1", UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)

        request = ProjectCreateRequest("_prebuild", UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)

        request = ProjectCreateRequest("CODECC_a1", UT_PROJECT_DISPLAY, UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)
    }

    @Test
    @DisplayName("测试非法项目显示名")
    fun `should throw exception with illegal display name`() {
        var request = ProjectCreateRequest(UT_PROJECT_ID, "", UT_PROJECT_DESC, true, UT_USER)
        assertThrows<ErrorCodeException> { projectService.createProject(request) }

        request = ProjectCreateRequest(UT_PROJECT_ID, "1".repeat(33), UT_PROJECT_DESC, true, UT_USER)
        assertThrows<ErrorCodeException> { projectService.createProject(request) }

        request = ProjectCreateRequest(UT_PROJECT_ID, "1".repeat(1), UT_PROJECT_DESC, true, UT_USER)
        assertThrows<ErrorCodeException> { projectService.createProject(request) }

        request = ProjectCreateRequest(UT_PROJECT_ID, "1".repeat(32), UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)

        removeAllProject()
        request = ProjectCreateRequest(UT_PROJECT_ID, "123-abc", UT_PROJECT_DESC, true, UT_USER)
        projectService.createProject(request)
    }

    @Test
    @DisplayName("测试查询项目")
    fun `test range query projects`() {
        var request = ProjectCreateRequest(
            "p1", "p1", "p1", true, UT_USER,
            listOf(ProjectMetadata("bg", "bg1"), ProjectMetadata("department", "dp1"))
        )
        projectService.createProject(request)

        request = ProjectCreateRequest(
            "p2", "p2", "p2", true, UT_USER,
            listOf(ProjectMetadata("bg", "bg2"), ProjectMetadata("department", "dp2"))
        )
        projectService.createProject(request)

        // 获取所有项目
        var records = projectService.rangeQuery(ProjectRangeQueryRequest(emptyList())).records
        Assertions.assertEquals(2, records.size)

        // 通过projectId查询
        records = projectService.rangeQuery(ProjectRangeQueryRequest(listOf("p2"))).records
        Assertions.assertEquals(1, records.size)
        Assertions.assertEquals("p2", records.first()!!.name)

        // 通过metadata查询
        var query = ProjectRangeQueryRequest(
            projectIds = emptyList(),
            projectMetadata = listOf(ProjectMetadata("bg", "bg2"))
        )
        records = projectService.rangeQuery(query).records
        Assertions.assertEquals(1, records.size)
        Assertions.assertEquals("p2", records.first()!!.name)

        query = ProjectRangeQueryRequest(
            projectIds = emptyList(),
            projectMetadata = listOf(ProjectMetadata("bg", "bg1"), ProjectMetadata("department", "dp1"))
        )
        records = projectService.rangeQuery(query).records
        Assertions.assertEquals(1, records.size)
        Assertions.assertEquals("p1", records.first()!!.name)

        query = ProjectRangeQueryRequest(
            projectIds = emptyList(),
            projectMetadata = listOf(ProjectMetadata("bg", "bg2"), ProjectMetadata("department", "dp1"))
        )
        records = projectService.rangeQuery(query).records
        Assertions.assertEquals(0, records.size)


        // projectId + metadata查询
        query = ProjectRangeQueryRequest(
            projectIds = listOf("p2"),
            projectMetadata = listOf(ProjectMetadata("bg", "bg2"))
        )
        records = projectService.rangeQuery(query).records
        Assertions.assertEquals(1, records.size)
        Assertions.assertEquals("p2", records.first()!!.name)

        query = ProjectRangeQueryRequest(
            projectIds = listOf("p1"),
            projectMetadata = listOf(ProjectMetadata("bg", "bg2"))
        )
        records = projectService.rangeQuery(query).records
        Assertions.assertEquals(0, records.size)
    }

    private fun removeAllProject() {
        projectDao.remove(Query())
    }
}
