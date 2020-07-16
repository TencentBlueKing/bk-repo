package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.model.DockerDigest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ContentUtilTest {

    @Test
    fun isEmptyBlobTest() {
        var digest = DockerDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d2")
        Assertions.assertEquals(ContentUtil.isEmptyBlob(digest), false)
        digest = DockerDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4")
        Assertions.assertEquals(ContentUtil.isEmptyBlob(digest), true)
    }
}