package com.tencent.bkrepo.pypi.artifact.url

import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import org.apache.commons.lang.StringUtils
import javax.servlet.http.HttpServletRequest

/**
 * pypi部分请求路径的信息无法定位一个文件，因此在PypiService之前构建一个完整坐标。
 * {package}/{version}/{filename}
 */
object UrlPatternUtil {

    fun HttpServletRequest.parameterMaps(): MutableMap<String, String> {
        val map = this.parameterMap
        val metadata: MutableMap<String, String> = mutableMapOf()
        // 对字符串数组做处理
        for (entry in map) {
            metadata[entry.key] = StringUtils.join(entry.value, ",")
        }
        return metadata
    }

    fun fileUpload(
        projectId: String,
        repoName: String,
        request: HttpServletRequest
    ): PypiArtifactInfo {
        val packageName: String = request.getParameter("name")
        val version: String = request.getParameter("version")
        return PypiArtifactInfo(projectId, repoName, "/$packageName/$version")
    }
}
