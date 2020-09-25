package com.tencent.bkrepo.maven

import com.tencent.bkrepo.maven.pojo.MavenGAVC
import org.apache.commons.lang.StringUtils

object MavenGAVCUtils {
    fun String.GAVC(): MavenGAVC {
        val pathList = this.removePrefix("/").removeSuffix("/").split("/")
        val jarName = pathList.last()
        val version = pathList[pathList.size - 2]
        val artifactId = pathList[pathList.size - 3]
        val groupId = StringUtils.join(pathList.subList(0, pathList.size - 3), ".")
        return MavenGAVC(
            jarName,
            groupId,
            artifactId,
            version,
            null
        )
    }
}
