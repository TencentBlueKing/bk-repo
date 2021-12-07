package com.tencent.bkrepo.maven.pojo

import com.tencent.bkrepo.maven.SNAPSHOT_SUFFIX
import org.apache.commons.lang3.StringUtils
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class MavenVersion(
    val artifactId: String,
    val version: String,
    var timestamp: String? = null,
    var buildNo: String? = null,
    var classifier: String? = null,
    val packaging: String
) {
    /**
     * e.g. test-1.0-20211206.112233.jar
     */
    fun combineToNonUnique(): String {
        val list = mutableListOf(artifactId, version)
        // 如果为pom 包，是不依赖环境的。
        if (packaging != "pom" && classifier != null) {
            list.add(classifier!!)
        }
        return "${StringUtils.join(list, '-')}.$packaging"
    }

    /**
     * e.g. test-1.0-SNAPSHOT.jar  >>  test-1.0-20211206.112233-1.jar
     */
    fun combineToUnique(no: Int? = 0): String {
        val timestampServer = if (timestamp == null) {
            ZonedDateTime.now(ZoneId.of("UTC")).format(formatter)
        } else timestamp

        val buildNoServer = if (buildNo == null) {
            no?.plus(1) ?: 1
        } else buildNo
        val list = mutableListOf(artifactId, version.removeSuffix(SNAPSHOT_SUFFIX), timestampServer, buildNoServer)
        if (packaging != "pom" && classifier != null) {
            list.add(classifier!!)
        }
        return "${StringUtils.join(list, '-')}.$packaging"
    }

    companion object {
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")
    }
}
