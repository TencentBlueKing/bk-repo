/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.repository.constant.PACKAGE_KEY_SEPARATOR
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import java.util.Locale

/**
 * 包唯一id工具类
 */
object PackageKeys {

    /**
     * 生成gav格式key
     */
    fun ofGav(groupId: String, artifactId: String): String {
        return StringBuilder(PackageType.MAVEN.schema).append(PACKAGE_KEY_SEPARATOR).append(groupId)
            .append(StringPool.COLON)
            .append(artifactId)
            .toString()
    }

    /**
     * 生成conan格式key
     *
     * 例子: conan://name
     */
    fun ofConan(name: String): String {
        return ofName(PackageType.CONAN.schema, name)
    }

    /**
     * 生成docker格式key
     *
     * 例子: docker://test
     */
    fun ofDocker(name: String): String {
        return ofName(PackageType.DOCKER.schema, name)
    }

    /**
     * 生成OCI格式key
     *
     * 例子: oci://test
     */
    fun ofOci(name: String): String {
        return ofName(PackageType.OCI.schema, name)
    }

    /**
     * 生成npm格式key
     *
     * 例子: npm://test
     */
    fun ofNpm(name: String): String {
        return ofName(PackageType.NPM.schema, name)
    }

    /**
     * 生成ohpm格式key
     *
     * 例子: ohpm://test
     */
    fun ofOhpm(name: String): String {
        return ofName(PackageType.OHPM.schema, name)
    }

    /**
     * 生成helm格式key
     *
     * 例子: helm://test
     */
    fun ofHelm(name: String): String {
        return ofName(PackageType.HELM.schema, name)
    }


    /**
     * 生成rpm格式key
     * 例子: rpm://test
     */
    fun ofRpm(path: String, name: String): String {
        return if (path.isNotBlank()) {
            StringBuilder(PackageType.RPM.schema).append(PACKAGE_KEY_SEPARATOR).append(path)
                .append(StringPool.SLASH)
                .append(name)
                .toString()
        } else {
            StringBuilder(PackageType.RPM.schema).append(PACKAGE_KEY_SEPARATOR)
                .append(name)
                .toString()
        }
    }

    /**
     * 生成pypi格式key
     * 例子: pypi://test
     */
    fun ofPypi(name: String): String {
        return ofName(PackageType.PYPI.schema, name)
    }

    /**
     * 生成composer格式key
     * 例子: composer://test
     */
    fun ofComposer(name: String): String {
        return ofName(PackageType.COMPOSER.schema, name)
    }

    /**
     * 生成nuget格式key
     * 例子: nuget://test
     */
    fun ofNuget(name: String): String {
        return ofName(PackageType.NUGET.schema, name)
    }

    /**
     * 生成huggingface格式key
     * 例子：huggingface://model/test
     */
    fun ofHuggingface(type: String, name: String): String {
        return ofName(PackageType.HUGGINGFACE.schema, "$type/$name")
    }

    /**
     * 生成cargo格式key
     * 例子: cargo://test
     */
    fun ofCargo(name: String): String {
        return ofName(PackageType.CARGO.schema, name)
    }

    /**
     * 生成gav格式key
     */
    fun resolveGav(gavKey: String): String {
        return resolveName(PackageType.MAVEN.schema, gavKey)
    }

    /**
     * 解析npm格式的key
     *
     * 例子: npm://test  ->  test
     */
    fun resolveNpm(npmKey: String): String {
        return resolveName(PackageType.NPM.schema, npmKey)
    }

    /**
     * 解析ohpm格式的key
     *
     * 例子: ohpm://test  ->  test
     */
    fun resolveOhpm(ohpmKey: String): String {
        return resolveName(PackageType.OHPM.schema, ohpmKey)
    }

    /**
     * 解析helm格式的key
     *
     * 例子: helm://test  ->  test
     */
    fun resolveHelm(helmKey: String): String {
        return resolveName(PackageType.HELM.schema, helmKey)
    }

    /**
     * 解析docker格式的key
     *
     * 例子: docker://test  ->  test
     */
    fun resolveDocker(dockerKey: String): String {
        return resolveName(PackageType.DOCKER.schema, dockerKey)
    }

    /**
     * 解析conan格式的key
     *
     * 例子: conan://test  ->  test
     */
    fun resolveConan(conanKey: String): String {
        return resolveName(PackageType.CONAN.schema, conanKey)
    }

    /**
     * 解析oci格式的key
     *
     * 例子: oci://test  ->  test
     */
    fun resolveOci(ociKey: String): String {
        return resolveName(PackageType.OCI.schema, ociKey)
    }

    /**
     * 解析rpm格式的key
     * 例子: rpm://test  ->  test
     */
    fun resolveRpm(rpmKey: String): String {
        return resolveName(PackageType.RPM.schema, rpmKey)
    }

    /**
     * 解析pypi格式的key
     *
     * 例子: pypi://test  ->  test
     */
    fun resolvePypi(pypiKey: String): String {
        return resolveName(PackageType.PYPI.schema, pypiKey)
    }

    /**
     * 解析composer格式的key
     *
     * 例子: composer://test  ->  test
     */
    fun resolveComposer(composerKey: String): String {
        return resolveName(PackageType.COMPOSER.schema, composerKey)
    }

    /**
     * 解析cargo格式的key
     *
     * 例子: cargo://test  ->  test
     */
    fun resolveCargo(cargoKey: String): String {
        return resolveName(PackageType.CARGO.schema, cargoKey)
    }

    /**
     * 解析huggingface格式的key
     *
     * 例子：huggingface://model/test -> model,test
     */
    fun resolveHuggingface(huggingfaceKey: String): Pair<String, String> {
        val key = resolveName(PackageType.HUGGINGFACE.schema, huggingfaceKey)
        val (type, name) = key.split(StringPool.SLASH, limit = 2)
        return Pair(type, name)
    }

    /**
     * 生成name格式key
     *
     * 例子: {schema}://test
     */
    fun ofName(schema: String, name: String): String {
        return StringBuilder(schema).append(PACKAGE_KEY_SEPARATOR).append(name).toString()
    }

    /**
     * 生成name格式key
     *
     * 例子: {schema}://test
     *
     * @param repositoryType repository type
     * @param name package name
     *
     * @return package key
     */
    fun ofName(repositoryType: RepositoryType, name: String): String {
        val schema = when (repositoryType) {
            RepositoryType.MAVEN -> PackageType.MAVEN.schema
            else -> repositoryType.name.lowercase(Locale.getDefault())
        }
        return ofName(schema, name)
    }

    /**
     * 解析nuget格式的key
     *
     * 例子: nuget://test  ->  test
     */
    fun resolveNuget(nugetKey: String): String {
        return resolveName(PackageType.NUGET.schema, nugetKey)
    }

    /**
     * 解析name格式key
     *
     * 例子: {schema}://test  ->  test
     */
    fun resolveName(schema: String, nameKey: String): String {
        val prefix = StringBuilder(schema).append(PACKAGE_KEY_SEPARATOR).toString()
        return nameKey.substringAfter(prefix)
    }
}
