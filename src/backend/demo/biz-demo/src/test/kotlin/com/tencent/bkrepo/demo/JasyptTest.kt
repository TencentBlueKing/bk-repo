package com.tencent.bkrepo.demo

import org.springframework.core.env.StandardEnvironment
import com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor
import org.junit.jupiter.api.Test


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