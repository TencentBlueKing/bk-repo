package com.tencent.bkrepo.maven.artifact

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class MavenArtifactInfoResolverTest {

    private val project = "bkrepo"
    private val repo = "maven"
    private val artifactUri = "org/slf4j/slf4j-api/1.7.30/slf4j-api-1.7.30.jar"

    @Test
    fun resolverTest() {
        val request = MockHttpServletRequest()
        val mavenArtifactInfo = MavenArtifactInfoResolver().resolve(
            project,
            repo,
            artifactUri,
            request
        )
        Assertions.assertEquals("org.slf4j", mavenArtifactInfo.groupId)
        Assertions.assertEquals("slf4j-api", mavenArtifactInfo.artifactId)
        Assertions.assertEquals("1.7.30", mavenArtifactInfo.versionId)
    }
}
