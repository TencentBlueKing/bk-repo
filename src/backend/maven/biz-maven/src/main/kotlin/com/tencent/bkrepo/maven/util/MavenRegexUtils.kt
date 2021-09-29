package com.tencent.bkrepo.maven.util

import com.tencent.bkrepo.maven.pojo.MavenSnapshotPojo
import java.util.regex.Pattern

object MavenRegexUtils {
    private const val SNAPSHOT_REGEX = "(-[0-9]+)?(\\.)?"

    fun resolverSnapshot(name: String): String? {
        val matcher = Pattern.compile(SNAPSHOT_REGEX).matcher(name)
        if (matcher.matches()) {
            val time = matcher.group(1)
            val buildNum = matcher.group(2)
            return time
        }
        return null
    }

}