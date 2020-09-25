package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.api.constant.StringPool

/**
 * 包唯一id工具类
 */
object PackageKeys {

    private const val DOCKER = "docker"
    private const val NPM = "npm"
    private const val SEPARATOR = "://"

    /**
     * 生成gav格式key
     */
    fun ofGav(groupId: String, artifactId: String): String {
        return StringBuilder("gav://").append(groupId)
            .append(StringPool.COLON)
            .append(artifactId)
            .toString()
    }

    /**
     * 生成name格式key
     *
     * 例子: {schema}://test
     */
    fun ofName(schema: String, name: String): String {
        return StringBuilder(schema).append(SEPARATOR).append(name).toString()
    }

    /**
     * 生成docker格式key
     *
     * 例子: docker://test
     */
    fun ofDocker(name: String): String {
        return ofName(DOCKER, name)
    }

    /**
     * 生成npm格式key
     *
     * 例子: npm://test
     */
    fun ofNpm(name: String): String {
        return ofName(NPM, name)
    }
}