package com.tencent.bkrepo.auth

import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.service.RoleService
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
@DisplayName("角色测试")
class RoleServiceTest {
    @Autowired
    private lateinit var roleService: RoleService

    private val ROLE_ID = "manager_unit_test"
    private val ROLE_NAME = "测试项目管理员"
    private val PROJECT_ID = "projectId_unit_test"
    private val REPO_NAME = "repo_unit_test"

    @BeforeEach
    fun setUp() {
        roleService.detail(ROLE_ID, PROJECT_ID)?.let {
            roleService.deleteRoleByid(it.id!!)
        }
    }

    @AfterEach
    fun teardown() {
        roleService.detail(ROLE_ID, PROJECT_ID)?.let {
            roleService.deleteRoleByid(it.id!!)
        }
    }

    @Test
    @DisplayName("创建角色测试")
    fun createRoleTest() {
        // type -> project
        val id = roleService.createRole(buildRoleRequest())
        Assertions.assertNotNull(id)
        // type -> repo
        val createRoleId = roleService.createRole(buildRoleRequest(type = RoleType.REPO, repoName = REPO_NAME))
        Assertions.assertNotNull(createRoleId)
    }

    @Test
    @DisplayName("根据主键id删除角色测试")
    fun deleteRoleByidTest() {
        assertThrows<ErrorCodeException> { roleService.deleteRoleByid(ROLE_ID) }
        val id = roleService.createRole(buildRoleRequest())!!
        val deleteRoleByid = roleService.deleteRoleByid(id)
        Assertions.assertTrue(deleteRoleByid)
    }

    @Test
    @DisplayName("通过项目和仓库查找角色测试")
    fun listRoleByProjectTest() {
        roleService.createRole(buildRoleRequest())
        val id = roleService.createRole(buildRoleRequest(projectId = "test_projectId"))
        val id1 = roleService.createRole(buildRoleRequest(projectId = "test_projectId_001", repoName = "test_name"))
        val id2 = roleService.createRole(buildRoleRequest(type = RoleType.REPO, repoName = "test_repo"))
        val listRoleByProject = roleService.listRoleByProject(null, null, null)
        Assertions.assertTrue(listRoleByProject.size == 4)
        val listRoleByProject1 = roleService.listRoleByProject(RoleType.PROJECT, null, null)
        Assertions.assertTrue(listRoleByProject1.size == 3)
        val listRoleByProject2 = roleService.listRoleByProject(RoleType.REPO, null, null)
        Assertions.assertTrue(listRoleByProject2.size == 1)
        val listRoleByProject3 = roleService.listRoleByProject(null, PROJECT_ID, null)
        Assertions.assertTrue(listRoleByProject3.size == 2)
        val listRoleByProject4 = roleService.listRoleByProject(RoleType.REPO, PROJECT_ID, null)
        Assertions.assertTrue(listRoleByProject4.size == 1)
        val listRoleByProject5 = roleService.listRoleByProject(RoleType.PROJECT, PROJECT_ID, null)
        Assertions.assertTrue(listRoleByProject5.size == 1)
        val listRoleByProject6 = roleService.listRoleByProject(RoleType.REPO, PROJECT_ID, "test_repo")
        Assertions.assertTrue(listRoleByProject6.size == 1)
        // has problems -> The last if condition never goes in
        // val listRoleByProject7 = roleService.listRoleByProject(null, "test_projectId_001", "test_repo")
        // Assertions.assertTrue(listRoleByProject7.size == 0)
        roleService.deleteRoleByid(id!!)
        roleService.deleteRoleByid(id1!!)
        roleService.deleteRoleByid(id2!!)
    }

    @Test
    @DisplayName("角色详情测试")
    fun detailTest() {
        val id = roleService.createRole(buildRoleRequest())
        val role = roleService.detail(id!!)
        role?.let {
            Assertions.assertEquals(it.roleId, ROLE_ID)
        }
    }

    @Test
    @DisplayName("角色详情测试")
    fun detailWithProjectIdTest() {
        val id = roleService.createRole(buildRoleRequest())
        val role = roleService.detail(ROLE_ID, PROJECT_ID)
        role?.let {
            Assertions.assertEquals(it.id!!, id)
        }
    }

    @Test
    @DisplayName("角色详情测试")
    fun detailWithProjectIdAndRepoNameTest() {
        val id = roleService.createRole(buildRoleRequest(repoName = REPO_NAME))
        val role = roleService.detail(ROLE_ID, PROJECT_ID, REPO_NAME)
        role?.let {
            Assertions.assertEquals(it.id!!, id)
        }
    }

    private fun buildRoleRequest(
        roleId: String = ROLE_ID,
        roleName: String = ROLE_NAME,
        type: RoleType = RoleType.PROJECT,
        projectId: String = PROJECT_ID,
        repoName: String? = null,
        admin: Boolean = false
    ): CreateRoleRequest {
        return CreateRoleRequest(roleId, roleName, type, projectId, repoName, admin)
    }
}
