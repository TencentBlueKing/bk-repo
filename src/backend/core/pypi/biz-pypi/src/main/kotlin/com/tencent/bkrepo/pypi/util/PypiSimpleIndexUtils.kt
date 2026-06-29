package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.constants.NON_ALPHANUMERIC_SEQ_REGEX
import com.tencent.bkrepo.pypi.constants.SIMPLE_INDEX_DIR_NAME
import com.tencent.bkrepo.pypi.constants.SIMPLE_INDEX_PACKAGE_LIST
import com.tencent.bkrepo.pypi.constants.SIMPLE_INDEX_PACKAGES_DIR
import com.tencent.bkrepo.pypi.constants.SIMPLE_INDEX_ROOT

object PypiSimpleIndexUtils {

    private val nonAlphanumericSeqRegex = Regex(NON_ALPHANUMERIC_SEQ_REGEX)

    fun getPackageListCachePath(): String = SIMPLE_INDEX_PACKAGE_LIST

    fun getPackageCachePath(packageName: String): String = "$SIMPLE_INDEX_PACKAGES_DIR/$packageName.html"

    fun isSimpleIndexPath(fullPath: String): Boolean = fullPath.startsWith(SIMPLE_INDEX_ROOT)

    fun isSimpleIndexFolder(name: String): Boolean = name == SIMPLE_INDEX_DIR_NAME

    fun normalizePackageName(name: String): String = name.replace(nonAlphanumericSeqRegex, "-").lowercase()

    fun packageCachePaths(packageName: String): Set<String> {
        val normalized = normalizePackageName(packageName)
        return setOf(getPackageCachePath(packageName), getPackageCachePath(normalized))
    }
}
