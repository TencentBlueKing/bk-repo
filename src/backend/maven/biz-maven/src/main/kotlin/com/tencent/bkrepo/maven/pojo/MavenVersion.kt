package com.tencent.bkrepo.maven.pojo

import org.apache.commons.lang3.StringUtils

data class MavenVersion(
    val artifactId: String,
    val version: String,
    var timestamp: String? = null,
    var buildNo: String? = null,
    var classifier: String? = null,
    val packaging: String
) {
    fun combineToNonUnique(): String {
        val list = mutableListOf(artifactId, version)
        if (packaging != "pom" && classifier != null) {
            list.add(classifier!!)
        }
        return "${StringUtils.join(list, '-')}.$packaging"
    }
}
