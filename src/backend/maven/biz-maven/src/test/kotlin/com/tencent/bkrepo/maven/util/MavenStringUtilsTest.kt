package com.tencent.bkrepo.maven.util

import com.tencent.bkrepo.maven.pojo.MavenVersion
import com.tencent.bkrepo.maven.util.MavenStringUtils.parseMavenFileName
import com.tencent.bkrepo.maven.util.MavenStringUtils.setVersion
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

internal class MavenStringUtilsTest {

    @Test
    fun resolverName() {
        val jarName = "my-app-4.0-20220110.065755-5-jar-with-dependencies.jar"
        val mavenVersion = MavenVersion(
            artifactId = "my-app",
            version = "4.0-SNAPSHOT",
            packaging = "jar"
        )
        mavenVersion.setVersion(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion.artifactId) },
            { Assertions.assertEquals("4.0-SNAPSHOT", mavenVersion.version) },
            { Assertions.assertEquals("20220110.065755", mavenVersion.timestamp) },
            { Assertions.assertEquals(5, mavenVersion.buildNo) },
            { Assertions.assertEquals("jar-with-dependencies", mavenVersion.classifier) },
            { Assertions.assertEquals("jar", mavenVersion.packaging) }
        )
        println("$mavenVersion")
    }

    @Test
    fun resolverName1() {
        val jarName = "my-app-4.0.jar"
        val mavenVersion = MavenVersion(
            artifactId = "my-app",
            version = "4.0",
            packaging = "jar"
        )
        mavenVersion.setVersion(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion.artifactId) },
            { Assertions.assertEquals("4.0", mavenVersion.version) },
            { Assertions.assertEquals(null, mavenVersion.timestamp) },
            { Assertions.assertEquals(null, mavenVersion.buildNo) },
            { Assertions.assertEquals(null, mavenVersion.classifier) },
            { Assertions.assertEquals("jar", mavenVersion.packaging) }
        )
        println("$mavenVersion")
    }

    @Test
    fun resolverName2() {
        val jarName = "my-app-4.0-jar-with-dependencies.jar"
        val mavenVersion = MavenVersion(
            artifactId = "my-app",
            version = "4.0",
            packaging = "jar"
        )
        mavenVersion.setVersion(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion.artifactId) },
            { Assertions.assertEquals("4.0", mavenVersion.version) },
            { Assertions.assertEquals(null, mavenVersion.timestamp) },
            { Assertions.assertEquals(null, mavenVersion.buildNo) },
            { Assertions.assertEquals("jar-with-dependencies", mavenVersion.classifier) },
            { Assertions.assertEquals("jar", mavenVersion.packaging) }
        )
        println("$mavenVersion")
    }

    @Test
    fun resolverName3() {
        val jarName = "my-app-4.0-20220110.065755-5.jar"
        val mavenVersion = MavenVersion(
            artifactId = "my-app",
            version = "4.0-SNAPSHOT",
            packaging = "jar"
        )
        mavenVersion.setVersion(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion.artifactId) },
            { Assertions.assertEquals("4.0-SNAPSHOT", mavenVersion.version) },
            { Assertions.assertEquals("20220110.065755", mavenVersion.timestamp) },
            { Assertions.assertEquals(5, mavenVersion.buildNo) },
            { Assertions.assertEquals(null, mavenVersion.classifier) },
            { Assertions.assertEquals("jar", mavenVersion.packaging) }
        )
        println("$mavenVersion")
    }

    @Test
    fun resolverName4() {
        val jarName = "my-app-4.0-jar-with-dependencies.jar"
        val mavenVersion = MavenVersion(
            artifactId = "my-app",
            version = "4.0-jar-with-dependencies",
            packaging = "jar"
        )
        mavenVersion.setVersion(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion.artifactId) },
            { Assertions.assertEquals("4.0-jar-with-dependencies", mavenVersion.version) },
            { Assertions.assertEquals(null, mavenVersion.timestamp) },
            { Assertions.assertEquals(null, mavenVersion.buildNo) },
            { Assertions.assertEquals(null, mavenVersion.classifier) },
            { Assertions.assertEquals("jar", mavenVersion.packaging) }
        )
        println("$mavenVersion")
    }

    @Test
    fun resolverName5() {
        val jarName = "my-app-4.0-SNAPSHOT-01-jar-with-dependencies.jar"
        val mavenVersion = MavenVersion(
            artifactId = "my-app",
            version = "4.0-SNAPSHOT-01",
            packaging = "jar"
        )
        mavenVersion.setVersion(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion.artifactId) },
            { Assertions.assertEquals("4.0-SNAPSHOT-01", mavenVersion.version) },
            { Assertions.assertEquals(null, mavenVersion.timestamp) },
            { Assertions.assertEquals(null, mavenVersion.buildNo) },
            { Assertions.assertEquals("jar-with-dependencies", mavenVersion.classifier) },
            { Assertions.assertEquals("jar", mavenVersion.packaging) }
        )
        println("$mavenVersion")
    }

    @Test
    fun resolverName6() {
        val jarName = "my-app-1.3.00+5b605e-20230606.062937-1.aar"
        val mavenVersion = MavenVersion(
            artifactId = "my-app",
            version = "1.3.00+5b605e-SNAPSHOT",
            packaging = "aar"
        )
        mavenVersion.setVersion(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion.artifactId) },
            { Assertions.assertEquals("1.3.00+5b605e-SNAPSHOT", mavenVersion.version) },
            { Assertions.assertEquals("20230606.062937", mavenVersion.timestamp) },
            { Assertions.assertEquals(1, mavenVersion.buildNo) },
            { Assertions.assertEquals(null, mavenVersion.classifier) },
            { Assertions.assertEquals("aar", mavenVersion.packaging) }
        )
        println("$mavenVersion")
    }

    @Test
    fun parseMavenFileNameTest() {
        var jarName = "my-app-4.0-20220110.065755-5-jar-with-dependencies.jar"
        var mavenVersion = parseMavenFileName(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion?.artifactId) },
            { Assertions.assertEquals("4.0-20220110.065755-5-jar-with-dependencies", mavenVersion?.version) },
            { Assertions.assertEquals("jar", mavenVersion?.packaging) }
        )

        jarName = "my-app-4.0.jar"
        mavenVersion = parseMavenFileName(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion?.artifactId) },
            { Assertions.assertEquals("4.0", mavenVersion?.version) },
            { Assertions.assertEquals("jar", mavenVersion?.packaging) }
        )

        jarName = "my-app-4.0-jar-with-dependencies.jar"
        mavenVersion = parseMavenFileName(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion?.artifactId) },
            { Assertions.assertEquals("4.0-jar-with-dependencies", mavenVersion?.version) },
            { Assertions.assertEquals("jar", mavenVersion?.packaging) }
        )


        jarName = "my-app-4.0-jar-with-dependencies.jar"
        mavenVersion = parseMavenFileName(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion?.artifactId) },
            { Assertions.assertEquals("4.0-jar-with-dependencies", mavenVersion?.version) },
            { Assertions.assertEquals("jar", mavenVersion?.packaging) }
        )

        jarName = "my-app-4.0-SNAPSHOT-01-jar-with-dependencies.jar"
        mavenVersion = parseMavenFileName(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion?.artifactId) },
            { Assertions.assertEquals("4.0-SNAPSHOT-01-jar-with-dependencies", mavenVersion?.version) },
            { Assertions.assertEquals("jar", mavenVersion?.packaging) }
        )

        jarName = "my-app-1.2.58.1-debug.aar"
        mavenVersion = parseMavenFileName(jarName)
        assertAll(
            { Assertions.assertEquals("my-app", mavenVersion?.artifactId) },
            { Assertions.assertEquals("1.2.58.1-debug", mavenVersion?.version) },
            { Assertions.assertEquals("aar", mavenVersion?.packaging) }
        )

        jarName = "my-app-client-a-2.8.5-tq-0.1.8.jar"
        mavenVersion = parseMavenFileName(jarName)
        assertAll(
            { Assertions.assertEquals("my-app-client-a", mavenVersion?.artifactId) },
            { Assertions.assertEquals("2.8.5-tq-0.1.8", mavenVersion?.version) },
            { Assertions.assertEquals("jar", mavenVersion?.packaging) }
        )

        jarName = "my-app-client-a-2.8.5-tq-0.1.8-source.jar"
        mavenVersion = parseMavenFileName(jarName)
        assertAll(
            { Assertions.assertEquals("my-app-client-a", mavenVersion?.artifactId) },
            { Assertions.assertEquals("2.8.5-tq-0.1.8-source", mavenVersion?.version) },
            { Assertions.assertEquals("jar", mavenVersion?.packaging) }
        )
    }
}
