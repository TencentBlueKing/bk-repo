package com.tencent.bkrepo.docker.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders

@DisplayName("repoServiceUtilTest")
@SpringBootTest
class RepoServiceUtilTest {

    @Test
    fun putHasStreamTest() {
        val httpHeader = HttpHeaders()
        val result = RepoServiceUtil.putHasStream(httpHeader)
        Assertions.assertNotEquals(result, true)
        httpHeader.set("User-Agent", "Go-http-client/1.1")
        Assertions.assertNotEquals(result, true)
    }

    @Test
    fun getDockerURITest() {
        val httpHeader = HttpHeaders()
        val path = "/docker/nginx"
        var result = RepoServiceUtil.getDockerURI(path, httpHeader)
        Assertions.assertNotEquals(result.port, 0)
        Assertions.assertEquals(result.host, "localhost")
        httpHeader.set("Host", "127.0.0.1:80")
        result = RepoServiceUtil.getDockerURI(path, httpHeader)
        Assertions.assertNotEquals(result.host, "localhost")
    }
}