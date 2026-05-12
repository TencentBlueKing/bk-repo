package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.common.api.constant.CharPool

object OciNameAliasCodec {
    private const val ESCAPE_PREFIX = "x0"
    private val HEX_REGEX = Regex("^[0-9a-f]+$")
    const val REQUEST_ATTR_PROJECT_ID_ENCODED = "oci.nameAlias.projectId.encoded"
    const val REQUEST_ATTR_REPO_NAME_ENCODED = "oci.nameAlias.repoName.encoded"

    fun encodeSegment(raw: String): String {
        if (raw.isEmpty()) return raw
        if (!raw.startsWith('_')) {
            return raw
        }
        val hex = raw.toByteArray(Charsets.UTF_8)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return ESCAPE_PREFIX + hex
    }

    fun decodeSegment(external: String): String {
        if (!external.startsWith(ESCAPE_PREFIX)) return external
        val hex = external.removePrefix(ESCAPE_PREFIX)
        if (hex.length % 2 != 0 || !HEX_REGEX.matches(hex)) return external
        return runCatching {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val raw = bytes.toString(Charsets.UTF_8)
            if (raw.startsWith('_') && encodeSegment(raw) == external) raw else external
        }.getOrElse { external }
    }

    fun encodeOciPath(path: String, encodeProject: Boolean = true, encodeRepo: Boolean = true): String {
        if (path.isBlank()) return path
        if (!encodeProject && !encodeRepo) return path
        val hasLeadingSlash = path.startsWith(CharPool.SLASH)
        val segments = path.trim(CharPool.SLASH).split(CharPool.SLASH).toMutableList()
        // 支持两种格式：/v2/{project}/{repo}/... 和 /{project}/{repo}/...
        val (projectIdx, repoIdx) = when {
            segments.size >= 3 && segments[0] == "v2" -> Pair(1, 2)
            segments.size >= 2 && segments[0] != "v2" -> Pair(0, 1)
            else -> return path
        }
        if (encodeProject) {
            segments[projectIdx] = encodeSegment(segments[projectIdx])
        }
        if (encodeRepo) {
            segments[repoIdx] = encodeSegment(segments[repoIdx])
        }
        val encoded = segments.joinToString(CharPool.SLASH.toString())
        return if (hasLeadingSlash) CharPool.SLASH + encoded else encoded
    }
}