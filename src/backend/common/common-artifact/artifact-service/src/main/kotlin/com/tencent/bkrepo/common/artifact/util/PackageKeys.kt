package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.api.constant.StringPool

/**
 * 包唯一id工具类
 */
object PackageKeys {

    private const val DOCKER = "docker"
    private const val NPM = "npm"
    private const val RPM = "rpm"
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

    /**
     * 生成rpm格式key
     *
     * 例子: rpm://test
     */
    fun ofRpm(path: String, name: String): String {
        return StringBuilder(RPM).append(SEPARATOR).append(path)
                .append(StringPool.COLON)
                .append(name)
                .toString()
    }

    /**
     * 解析npm格式的key
     *
     * 例子: npm://test  ->  test
     */
    fun resolveNpm(npmKey: String): String {
        return resolveName(NPM, npmKey)
    }

    /**
     * 解析docker格式的key
     *
     * 例子: docker://test  ->  test
     */
    fun resolveDocker(dockerKey: String): String {
        return resolveName(DOCKER, dockerKey)
    }

    /**
     * 解析rpm格式的key
     *
     * 例子: rpm://test  ->  test
     */
    fun resolveRpm(rpmKey: String): String {
        return resolveName(RPM, rpmKey)
    }

    /**
     * 解析name格式key
     *
     * 例子: {schema}://test  ->  test
     */
    fun resolveName(schema: String, nameKey: String): String {
        val prefix = StringBuilder(schema).append(SEPARATOR).toString()
        return nameKey.substringAfter(prefix)
    }
}