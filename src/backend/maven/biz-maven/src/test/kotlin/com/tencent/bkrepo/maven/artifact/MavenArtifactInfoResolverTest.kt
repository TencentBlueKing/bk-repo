package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.api.util.readXmlString
import com.tencent.bkrepo.maven.pojo.MavenPom
import com.tencent.bkrepo.maven.pojo.MavenSnapshot
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.io.FileInputStream

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

    @Test
    fun mavenPomTest1() {
        val fileInputStream = FileInputStream("/Users/Weaving/Downloads/spring-boot-build-1.0.0.RELEASE.pom")
        val mavenPom = fileInputStream.readXmlString<MavenPom>()
        Assertions.assertEquals("1.0.0.RELEASE", mavenPom.version)
    }

    @Test
    fun mavenPomTes2t() {
        val fileInputStream = FileInputStream("/Users/Weaving/Downloads/bksdk-1.0.0-20200928.015515-1 (1).pom")
        val mavenPom = fileInputStream.readXmlString<MavenPom>()
        Assertions.assertEquals("1.0.0-SNAPSHOT", mavenPom.version)
    }

    @Test
    fun mavenSnapshotTest() {
        val fileInputStream = FileInputStream("/Users/Weaving/Downloads/maven-metadata.xml.1")
        val mavenSnapshot = fileInputStream.readXmlString<MavenSnapshot>()
        Assertions.assertEquals(
            "1.0.0-20200928.033656-1",
            mavenSnapshot.versioning.snapshotVersions[0].value
        )
    }
}
