package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.util.ResponseUtil.isEmptyBlob
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ManifestUtilTest {

    @Test
    fun isEmptyBlobTest() {
        var digest = DockerDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d2")
        Assertions.assertEquals(isEmptyBlob(digest), false)
        digest = DockerDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4")
        Assertions.assertEquals(isEmptyBlob(digest), true)
    }
}