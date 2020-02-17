package com.tencent.bkrepo.pypi.artifact.url

import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import net.sf.json.JSONArray
import javax.servlet.http.HttpServletRequest

/**
 * pypi部分请求路径的信息无法定位一个文件，因此在PypiService之前构建一个完整坐标。
 * {package}/{version}/{filename}
 */

object UrlPatternUtil {

    fun fileUpload(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): PypiArtifactInfo {
        val packageName: String = request.getParameter("name")
        val version: String = request.getParameter("version")
        val classifiersList = request.getParameterValues("classifiers")
        val classifiers = JSONArray.fromObject(classifiersList)
        val metadata = mapOf<String, String>(
            "name" to (request.getParameter("name")),
            "version" to request.getParameter("version"),
            "filetype" to request.getParameter("filetype"),
            "pyversion" to (request.getParameter("pyversion") ?: ""),
            "metadata_version" to (request.getParameter("metadata_version") ?: ""),
            "summary" to (request.getParameter("summary") ?: ""),
            "home_page" to (request.getParameter("author") ?: ""),
            "author" to (request.getParameter("version") ?: ""),
            "author_email" to (request.getParameter("author_email") ?: ""),
            "maintainer" to (request.getParameter("maintainer") ?: ""),
            "maintainer_email" to (request.getParameter("maintainer_email") ?: ""),
            "license" to (request.getParameter("license") ?: ""),
            "description" to (request.getParameter("description") ?: ""),
            "keywords" to (request.getParameter("keywords") ?: ""),
            "platform" to (request.getParameter("platform") ?: ""),
            "classifiers" to (classifiers.toString() ?: ""),
            "download_url" to (request.getParameter("download_url") ?: ""),
            "comment" to (request.getParameter("comment") ?: ""),
            "md5_digest" to (request.getParameter("md5_digest") ?: ""),
            "sha256_digest" to (request.getParameter("sha256_digest") ?: ""),
            "blake2_256_digest" to (request.getParameter("blake2_256_digest") ?: ""),
            "requires_python" to (request.getParameter("requires_python") ?: ""),
            "description_content_type" to (request.getParameter("description_content_type") ?: ""),
            "action" to (request.getParameter("action") ?: ""),
            "protocol_version" to (request.getParameter("protocol_version") ?: "")
        )
        return PypiArtifactInfo(projectId, repoName, "/$packageName/$version", packageName, version, metadata)
    }
}
