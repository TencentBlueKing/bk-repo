package com.tencent.bkrepo.npm.service

import com.tencent.bkrepo.npm.pojo.module.des.service.DepsCreateRequest
import com.tencent.bkrepo.npm.pojo.module.des.service.DepsDeleteRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("npm依赖关系测试")
class ModuleDepsServiceTest {
    @Autowired
    private lateinit var moduleDepsService: ModuleDepsService

    @Test
    @DisplayName("npm新增依赖关系测试")
    fun createDepsTest() {
        val depsCreateRequest = DepsCreateRequest("test", "npm-local", "underscore", "code")
        val moduleDepsInfo = moduleDepsService.create(depsCreateRequest)
        Assertions.assertEquals(moduleDepsInfo.name, "underscore")
    }

    @Test
    @DisplayName("npm依赖关系删除测试")
    fun deleteDepsTest() {
        val depsDeleteRequest = DepsDeleteRequest("test", "npm-local", "underscore", "code", "system")
        moduleDepsService.delete(depsDeleteRequest)
    }

    @Test
    @DisplayName("npm依赖列表测试")
    fun listDepsTest() {
        val list = moduleDepsService.list("test", "npm-local", "helloworld-npm-publish")
        Assertions.assertEquals(list.size, 0)
    }

    @Test
    @DisplayName("npm依赖分页测试")
    fun pageDepsTest() {
        val page = moduleDepsService.page("test", "npm-local",0,20, "babel-messages")
        Assertions.assertEquals(page.count, 1)
    }
}