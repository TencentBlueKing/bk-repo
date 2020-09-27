package com.tencent.bkrepo.maven.util

import com.tencent.bkrepo.maven.pojo.MavenGAVC
import org.apache.commons.lang.StringUtils
import javax.servlet.http.HttpServletRequest

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

    fun HttpServletRequest.getMavenGAVC(): MavenGAVC {
        val packageKey = this.getParameter("packageKey")
        val version = this.getParameter("version")
        val artifactId = packageKey.split(":").last()
        val groupId = packageKey.removePrefix("gav://").split(":")[0]
        return MavenGAVC(null, groupId, artifactId, version, null)
    }
}
