package com.tencent.bkrepo.rpm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.rpm.util.StrUtils.formatSeparator
import org.apache.commons.lang.StringUtils
import org.springframework.web.HttpRequestHandler
import javax.xml.ws.spi.http.HttpContext

class RpmArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val RPM = "/{projectId}/{repoName}/**"
        const val RPM_CONFIGURATION = "/configuration/{projectId}/{repoName}/**"
        const val RPM_DEBUG_FLUSH = "/flush/{projectId}/{repoName}/**"
        const val RPM_DEBUG_ALL_FLUSH = "/flushAll/{projectId}/{repoName}/"

        //RPM 产品接口
        const val MAVEN_EXT_DETAIL = "/version/detail/{projectId}/{repoName}"
        const val MAVEN_EXT_PACKAGE_DELETE = "/package/delete/{projectId}/{repoName}"
        const val MAVEN_EXT_VERSION_DELETE = "/version/delete/{projectId}/{repoName}"
    }

    override fun getArtifactFullPath(): String {
        val packageKey = HttpContextHolder.getRequest().getParameter("packageKey")
        val version = HttpContextHolder.getRequest().getParameter("version")
        return if (StringUtils.isBlank(packageKey)) {
            super.getArtifactFullPath()
        } else {
            val rpmInfoList = PackageKeys.resolveRpm(packageKey).split(":")
            val path = rpmInfoList.first().formatSeparator(".", "/")
            val name = rpmInfoList.last()
            "/$path/$name-$version.rpm"
        }
    }
}
