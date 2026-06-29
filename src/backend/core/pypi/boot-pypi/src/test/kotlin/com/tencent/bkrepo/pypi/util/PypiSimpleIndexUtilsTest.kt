package com.tencent.bkrepo.pypi.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PypiSimpleIndexUtilsTest {

    @Test
    fun getPackageListCachePath() {
        assertEquals("/.pypi-simple-index/package-list.html", PypiSimpleIndexUtils.getPackageListCachePath())
    }

    @Test
    fun getPackageCachePath() {
        assertEquals("/.pypi-simple-index/packages/requests.html", PypiSimpleIndexUtils.getPackageCachePath("requests"))
    }

    @Test
    fun isSimpleIndexPath() {
        assertTrue(PypiSimpleIndexUtils.isSimpleIndexPath("/.pypi-simple-index/package-list.html"))
        assertFalse(PypiSimpleIndexUtils.isSimpleIndexPath("/requests/1.0.0/pkg.whl"))
    }

    @Test
    fun isSimpleIndexFolder() {
        assertTrue(PypiSimpleIndexUtils.isSimpleIndexFolder(".pypi-simple-index"))
        assertFalse(PypiSimpleIndexUtils.isSimpleIndexFolder("requests"))
    }

    @Test
    fun normalizePackageName() {
        assertEquals("my-package", PypiSimpleIndexUtils.normalizePackageName("My_Package"))
    }

    @Test
    fun packageCachePathsIncludesNormalizedName() {
        assertEquals(
            setOf(
                "/.pypi-simple-index/packages/My_Package.html",
                "/.pypi-simple-index/packages/my-package.html"
            ),
            PypiSimpleIndexUtils.packageCachePaths("My_Package")
        )
    }
}
