package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException

/**
 * 节点相关工具类
 *
 * @author: carrypan
 * @date: 2019-09-24
 */
object NodeUtils {

    private val forbiddenNameList = listOf(".", "..")
    /**
     * 最大目录深度
     */
    private const val MAX_DIR_DEPTH = 10

    /**
     * 文件名称最大长度
     */
    private const val MAX_FILENAME_LENGTH = 128

    /**
     * 文件分隔符
     */
    private const val FILE_SEPARATOR = "/"

    /**
     * 根目录
     */
    const val ROOT_DIR = FILE_SEPARATOR

    /**
     * 解析目录名称，返回目录数组。
     * 去除首尾空格，出错则抛出异常
     */
    fun parseDirName(input: String): String {
        val dirName = input.trim()
        dirName.takeIf { it.startsWith(FILE_SEPARATOR) }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_INVALID, "Directory name {$dirName} is invalid, it should start with '$FILE_SEPARATOR'.")

        val nameList = dirName.split(FILE_SEPARATOR).filter { it.isNotBlank() }.map { parseFileName(it) }.toList()
        nameList.takeIf { it.size <= MAX_DIR_DEPTH }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_INVALID, "The depth of directory should not exceed $MAX_DIR_DEPTH.")

        return ROOT_DIR + nameList.joinToString(FILE_SEPARATOR)
    }

    /**
     * 处理并验证文件名称
     * 不能包含/，不能全为空，不能超过指定长度
     */
    fun parseFileName(input: String): String {
        val fileName = input.trim()
        fileName.takeIf { it.isNotBlank() }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_INVALID, "File name can not be blank.")
        fileName.takeUnless { forbiddenNameList.contains(it) }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_INVALID)
        fileName.takeUnless { it.contains(FILE_SEPARATOR) }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_INVALID, "File name {$input} should not contain '$FILE_SEPARATOR'.")
        fileName.takeIf { it.length <= MAX_FILENAME_LENGTH }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_INVALID, "The length of name {$input} should not exceed $MAX_FILENAME_LENGTH.")
        return fileName
    }

    /**
     * 根据路径列表和文件名组合全路径
     */
    fun combineFullPath(path: String, name: String): String {
        return if (path == ROOT_DIR) ROOT_DIR + name else "${path}$FILE_SEPARATOR$name"
    }

    /**
     * 获取父级目录
     */
    fun getParentPath(path: String): String {
        return if (path == ROOT_DIR || path.isBlank()) {
            ""
        } else {
            val subString = path.substring(0, path.lastIndexOf("/"))
            if (subString.isBlank()) ROOT_DIR else subString
        }
    }

    /**
     * 根据目录获取文件名称
     */
    fun getName(path: String): String {
        return if (path == ROOT_DIR || path.isBlank()) {
            ROOT_DIR
        } else {
            path.substring(path.lastIndexOf("/") + 1)
        }
    }
}
