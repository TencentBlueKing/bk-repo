package com.tencent.bkrepo.maven.util

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder

/**
 * 对 Maven 仓内可在浏览器渲染的内容补充响应头，保留静态页面展示并限制脚本执行。
 */
object MavenActiveContentHeaders {
    private val ACTIVE_EXTENSIONS = setOf("html", "htm", "xhtml", "svg", "js", "mjs")

    /**
     * 允许静态样式/图片，限制 script/object 执行。
     */
    private const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; style-src 'unsafe-inline' 'self'; " +
            "img-src 'self' data: https: http:; font-src 'self' data:; " +
            "script-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"

    private const val HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
    private const val HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy"
    private const val NOSNIFF = "nosniff"

    fun applyIfActiveContent(artifactName: String) {
        val extension = PathUtils.resolveExtension(artifactName).lowercase()
        if (extension !in ACTIVE_EXTENSIONS) {
            return
        }
        val response = HttpContextHolder.getResponseOrNull() ?: return
        response.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, NOSNIFF)
        response.setHeader(HEADER_CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY)
    }
}
