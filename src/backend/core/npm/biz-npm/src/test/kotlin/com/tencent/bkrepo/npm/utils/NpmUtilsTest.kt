package com.tencent.bkrepo.npm.utils

import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NpmUtilsTest {

    @Test
    fun testBuildPackageTgzTarball() {
        val oldTarball =
            "http://bkrepo.example.com/npm/blueking/npm-local/@test/bkrepo-test/-/@test/bkrepo-test-1.0.0.tgz"
        val domain = "http://bkrepo.example.com/npm"
        val tarballPrefix = "http://bkrepo.example.com/npm"
        val name = "@test/bkrepo-test"
        val artifactInfo = NpmArtifactInfo(projectId = "blueking", repoName = "npm-local", artifactUri = "/")

        // not return repo id
        var tarball = NpmUtils.buildPackageTgzTarball(
            oldTarball, domain, tarballPrefix, false, name, artifactInfo
        )
        assertEquals(
            "$tarballPrefix/@test/bkrepo-test/-/@test/bkrepo-test-1.0.0.tgz",
            tarball
        )

        // return repo id
        tarball = NpmUtils.buildPackageTgzTarball(
            oldTarball, domain, tarballPrefix, true, name, artifactInfo
        )
        assertEquals(
            "$tarballPrefix/blueking/npm-local/@test/bkrepo-test/-/@test/bkrepo-test-1.0.0.tgz",
            tarball
        )

        // tarball prefix is empty
        tarball = NpmUtils.buildPackageTgzTarball(
            oldTarball, domain, "", false, name, artifactInfo
        )
        assertEquals(
            "$domain/blueking/npm-local/@test/bkrepo-test/-/@test/bkrepo-test-1.0.0.tgz",
            tarball
        )
    }
}
