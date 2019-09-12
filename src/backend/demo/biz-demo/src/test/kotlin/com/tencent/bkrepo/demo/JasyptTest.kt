package com.tencent.bkrepo.demo

import com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor
import org.junit.jupiter.api.Test
import org.springframework.core.env.StandardEnvironment

/**
 * Jasypt 加密测试
 *
 * @author: carrypan
 * @date: 2019-09-11
 */
class JasyptTest {

    @Test
    fun testJasypt() {
        System.setProperty("jasypt.encryptor.password", "bkrepo")
        val stringEncryptor = DefaultLazyEncryptor(StandardEnvironment())
        println(stringEncryptor.encrypt("pass@bkrepo"))
    }
}
