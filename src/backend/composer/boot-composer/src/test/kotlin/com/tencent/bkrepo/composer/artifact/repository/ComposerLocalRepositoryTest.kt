package com.tencent.bkrepo.composer.artifact.repository

import com.tencent.bkrepo.composer.util.HttpUtil.requestAddr
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpServletRequest

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComposerLocalRepositoryTest {

    companion object {
        private const val requestAddrFormat = "%s://%s:%d"
        private val httpRequest = MockHttpServletRequest()
        private val httpsRequest = MockHttpServletRequest()
    }

    @BeforeAll
    fun init() {
        with(httpsRequest) {
            protocol = "https"
            remoteHost = "192.168.0.1"
            remotePort = 8080
        }
        with(httpRequest) {
            protocol = "http"
            remoteHost = "192.168.0.1"
            remotePort = 8080
        }
    }

    @Test
    fun requestAddrTest() {
        Assertions.assertEquals("https://192.168.0.1:8080", httpsRequest.requestAddr())
        Assertions.assertEquals("http://192.168.0.1:8080", httpRequest.requestAddr())
    }
}
