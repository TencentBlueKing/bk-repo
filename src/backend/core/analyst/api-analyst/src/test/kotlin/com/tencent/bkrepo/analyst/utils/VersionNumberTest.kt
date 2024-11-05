package com.tencent.bkrepo.analyst.utils

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class VersionNumberTest {
    @Test
    fun testCreateVersionNumber() {
        var version = VersionNumber("v0.0.1.2-alpha.1+001")
        val versionCore = IntArray(4)
        versionCore[0] = 0
        versionCore[1] = 0
        versionCore[2] = 1
        versionCore[3] = 2
        Assertions.assertArrayEquals(versionCore, version.versionCore)
        Assertions.assertArrayEquals(listOf("alpha", "1").toTypedArray(), version.preRelease!!.toTypedArray())
        Assertions.assertEquals("001", version.build)


        version = VersionNumber("0.0.1.2-alpha.1")
        Assertions.assertArrayEquals(versionCore, version.versionCore)
        Assertions.assertArrayEquals(listOf("alpha", "1").toTypedArray(), version.preRelease!!.toTypedArray())
        Assertions.assertEquals(null, version.build)

        version = VersionNumber("0.0.1.2+001")
        Assertions.assertArrayEquals(versionCore, version.versionCore)
        Assertions.assertEquals(null, version.preRelease)
        Assertions.assertEquals("001", version.build)

        Assertions.assertThrows(VersionNumber.UnsupportedVersionException::class.java) {
            VersionNumber( "0.0.1.2-alp+ha.1+001")
        }
    }

    /**
     * 比较规则参考：https://semver.org/#spec-item-11
     * 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
     */
    @Test
    fun testCompare() {
        val version = VersionNumber("0.1.2.3")
        Assertions.assertTrue(version.lt(VersionNumber("0.1.2.3.4")))
        Assertions.assertTrue(version.lte(VersionNumber("0.1.2.3")))
        Assertions.assertTrue(version.eq(VersionNumber("0.1.2.3")))
        Assertions.assertTrue(version.gt(VersionNumber("0.1.2")))
        Assertions.assertTrue(version.gte(VersionNumber("0.1.2.3")))

        // 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta
        // < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
        Assertions.assertTrue(VersionNumber("1.0.0-alpha").lt(VersionNumber("1.0.0-alpha.1")))
        Assertions.assertTrue(VersionNumber("1.0.0-alpha.1").lt(VersionNumber("1.0.0-alpha.beta")))
        Assertions.assertTrue(VersionNumber("1.0.0-alpha.beta").lt(VersionNumber("1.0.0-beta")))
        Assertions.assertTrue(VersionNumber("1.0.0-beta").lt(VersionNumber("1.0.0-beta.2")))
        Assertions.assertTrue(VersionNumber("1.0.0-beta.2").lt(VersionNumber("1.0.0-beta.11")))
        Assertions.assertTrue(VersionNumber("1.0.0-beta.11").lt(VersionNumber("1.0.0-rc.1")))
        Assertions.assertTrue(VersionNumber("1.0.0-rc.1").lt(VersionNumber("1.0.0")))
    }
}
