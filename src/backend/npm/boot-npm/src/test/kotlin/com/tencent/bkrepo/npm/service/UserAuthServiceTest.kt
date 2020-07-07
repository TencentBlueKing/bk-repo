package com.tencent.bkrepo.npm.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("npm用户认证测试")
class UserAuthServiceTest {
    @Autowired
    private lateinit var authService: NpmAuthService

    @Test
    @DisplayName("用户登录测试")
    fun addUserTest(){
        val body = "{_id:xwhy,name:xwhy,password:123456}"
        val authResponse = authService.addUser(body)
        Assertions.assertEquals(authResponse.ok,true)
        Assertions.assertEquals(authResponse.id,"xwhy")
        Assertions.assertNotEquals(authResponse.token,true)
    }
}
