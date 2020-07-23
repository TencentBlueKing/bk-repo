package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.model.DockerDigest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders

@DisplayName("repoServiceUtilTest")
@SpringBootTest
class ResponseUtilTest {

    @Test
    @DisplayName("测试http头判断")
    fun putHasStreamTest() {
        val httpHeader = HttpHeaders()
        val result = ResponseUtil.putHasStream(httpHeader)
        Assertions.assertNotEquals(result, true)
        httpHeader.set("User-Agent", "Go-http-client/1.1")
        Assertions.assertNotEquals(result, true)
    }

    @Test
    @DisplayName("测试URI路径获取")
    fun getDockerURITest() {
        val httpHeader = HttpHeaders()
        val path = "/docker/nginx"
        var result = ResponseUtil.getDockerURI(path, httpHeader)
        Assertions.assertNotEquals(result.port, 0)
        Assertions.assertEquals(result.host, "localhost")
        httpHeader.set("Host", "127.0.0.1:80")
        result = ResponseUtil.getDockerURI(path, httpHeader)
        Assertions.assertNotEquals(result.host, "localhost")
    }

    @Test
    @DisplayName("测试空的blob文件")
    fun isEmptyBlobTest() {
        var digest = DockerDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d2")
        Assertions.assertEquals(ResponseUtil.isEmptyBlob(digest), false)
        digest = DockerDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4")
        Assertions.assertEquals(ResponseUtil.isEmptyBlob(digest), true)
    }
}