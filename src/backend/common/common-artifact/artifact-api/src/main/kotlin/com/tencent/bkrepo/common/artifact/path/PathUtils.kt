package com.tencent.bkrepo.common.artifact.path

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_PATH_INVALID

/**
 * 节点相关工具类
 *
 * path节点目录命名规则：以'/'开头，以'/'结尾
 * name节点文件命名规则：不含'/'
 * fullPath全路径命名规则：以'/'开头，结尾不含'/'
 */
object PathUtils {

    private val forbiddenNameList = listOf(".", "..")

    private val keywordList = listOf("\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|", "?", "&")
    /**
     * 最大目录深度
     */
    private const val MAX_DIR_DEPTH = 64

    /**
     * 文件名称最大长度
     */
    private const val MAX_FILENAME_LENGTH = 1024

    /**
     * 文件分隔符
     */
    const val SEPARATOR = "/"

    /**
     * 文件分隔字符
     */
    private const val SEPARATOR_CHAR = '/'

    /**
     * 根目录
     */
    const val ROOT = SEPARATOR

    /**
     * 格式化目录名称, 返回格式/a/b/c/，根目录返回/
     * /a/b/c -> /a/b/c/
     * /a/b/c/ -> /a/b/c/
     */
    fun formatPath(input: String): String {
        val path = formatFullPath(input)
        return if (isRoot(path)) ROOT else path + SEPARATOR
    }

    /**
     * 格式化全路径名称, 返回格式/a/b/c，根目录返回/
     */
    fun formatFullPath(input: String): String {
        val path = input.trim()
        if (isRoot(path)) return ROOT

        val nameList = path.split(SEPARATOR).filter { it.isNotBlank() }.map { it.trim() }.toList()
        val builder = StringBuilder()
        nameList.forEach { builder.append(SEPARATOR).append(it) }
        return builder.toString()
    }

    /**
     * 解析目录名称，返回格式/a/b/c/，根目录返回/
     * 出错则抛出异常
     */
    fun parseFullPath(input: String): String {
        val fullPath = input.trim()
        if (isRoot(fullPath)) return ROOT

        fullPath.takeIf { it.startsWith(SEPARATOR) } ?: throw ErrorCodeException(NODE_PATH_INVALID, input)

        val nameList = fullPath.split(SEPARATOR).filter { it.isNotBlank() }.map {
            parseFileName(it)
        }.toList()
        nameList.takeIf { it.size <= MAX_DIR_DEPTH } ?: throw ErrorCodeException(NODE_PATH_INVALID, input)

        val builder = StringBuilder()
        nameList.forEach { builder.append(SEPARATOR).append(it) }

        return builder.toString()
    }

    /**
     * 处理并验证文件名称，返回格式abc.txt
     * 不能包含/，不能全为空，不能超过指定长度
     */
    fun parseFileName(input: String): String {
        val fileName = input.trim()
        fileName.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(NODE_PATH_INVALID, input)
        fileName.takeUnless { forbiddenNameList.contains(it) } ?: throw ErrorCodeException(NODE_PATH_INVALID)
        fileName.takeUnless { it.contains(SEPARATOR) } ?: throw ErrorCodeException(NODE_PATH_INVALID, input)
        fileName.takeIf { it.length <= MAX_FILENAME_LENGTH } ?: throw ErrorCodeException(NODE_PATH_INVALID, input)
        return fileName
    }

    /**
     * 根据路径列表和文件名组合全路径，返回格式/a/b/c/abc.txt
     *
     * /a/b/c + d -> /a/b/c/d
     * /a/b/c/ + d -> /a/b/c/d
     */
    fun combineFullPath(path: String, name: String): String {
        return if (!path.endsWith(SEPARATOR)) path + SEPARATOR + name else path + name
    }

    /**
     * 根据路径列表和文件名组合新的路径，返回格式/a/b/c/
     *
     * /a/b/c + d -> /a/b/c/d/
     * /a/b/c/ + d -> /a/b/c/d/
     */
    fun combinePath(parent: String, name: String): String {
        val parentPath = if (!parent.endsWith(SEPARATOR)) parent + SEPARATOR else parent
        val newPath = parentPath + name.trimStart(SEPARATOR_CHAR)
        return if (!newPath.endsWith(SEPARATOR)) newPath + SEPARATOR else newPath
    }

    /**
     * 根据fullPath解析目录名称, 返回格式/a/b/c/
     *
     * /a/b/c -> /a/b/
     * /a/b/c/ -> /a/b/
     */
    fun resolvePath(fullPath: String): String {
        val index = fullPath.trimEnd(SEPARATOR_CHAR).lastIndexOf(SEPARATOR)
        return if (isRoot(fullPath) || index <= 0) ROOT else fullPath.substring(0, index + 1)
    }

    /**
     * 根据fullPath解析文件名称，返回格式abc.txt
     *
     * /a/b/c -> c
     * /a/b/c/ -> c
     */
    fun resolveName(fullPath: String): String {
        val trimmedPath = fullPath.trimEnd(SEPARATOR_CHAR)
        return if (isRoot(trimmedPath)) {
            StringPool.EMPTY
        } else {
            trimmedPath.substring(trimmedPath.lastIndexOf(SEPARATOR) + 1)
        }
    }

    /**
     * 解析文件后缀
     */
    fun resolveExtension(fileName: String): String? {
        return fileName.trim().substring(fileName.lastIndexOf(StringPool.DOT) + 1)
    }

    /**
     * 判断路径是否为根目录
     */
    fun isRoot(path: String): Boolean {
        return path == ROOT || path == ""
    }

    /**
     * 正则特殊符号转义
     */
    fun escapeRegex(input: String): String {
        var escapedString = input.trim()
        if (escapedString.isNotBlank()) {
            keywordList.forEach {
                if (escapedString.contains(it)) {
                    escapedString = escapedString.replace(it, "\\$it")
                }
            }
        }
        return escapedString
    }
}
