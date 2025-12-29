/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.npm.model.metadata

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.npm.constants.VERSION
import java.io.IOException
import java.io.Serializable

/**
 * npm version metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NpmVersionMetadata : Serializable {
    var name: String? = null
    var description: String? = null
    var tags: JsonNode? = null
    var version: String? = null
    var author: JsonNode? = null
    var homepage: JsonNode? = null
    var repository: JsonNode? = null
    var contributors: JsonNode? = null
    var bugs: JsonNode? = null
    var engines: JsonNode? = null
    var main: JsonNode? = null

    @JsonProperty("_id")
    var id: String? = null

    @JsonProperty("dist")
    var dist: Dist? = null
    var dependencies: Any? = null
        set(dependencies) {
            field = if (dependencies != null) resolveDependencies(dependencies) else emptyMap()
        }
    var optionalDependencies: Any? = null
    var devDependencies: Any? = null
        set(devDependencies) {
            field = if (devDependencies != null) resolveDependencies(devDependencies) else emptyMap()
        }
    var bundledDependencies: JsonNode? = null
    var peerDependencies: JsonNode? = null
    var keywords: JsonNode? = null
    var license: JsonNode? = null
    var licenses: JsonNode? = null
    var files: JsonNode? = null
    var bin: JsonNode? = null
    var man: JsonNode? = null
    var maintainers: JsonNode? = null
    var directories: JsonNode? = null
    var scripts: JsonNode? = null
    var config: JsonNode? = null
    var engineStrict: JsonNode? = null
    var os: JsonNode? = null
    var cpu: JsonNode? = null
    var preferGlobal: JsonNode? = null

    @JsonProperty("private")
    var privatePackage: JsonNode? = null
    var publishConfig: JsonNode? = null
    @JsonProperty("_from")
    var from: JsonNode? = null
    @JsonProperty("_npmVersion")
    var npmVersion: JsonNode? = null
    @JsonProperty("_npmUser")
    var npmUser: JsonNode? = null

    @JsonIgnore
    var lastModified: Long = 0
    private var other: MutableMap<String, Any?> = mutableMapOf()

    @JsonAnySetter
    fun set(name: String, value: Any?) {
        this.other[name] = value
    }

    @JsonAnyGetter
    fun any(): Map<String, Any?> {
        return this.other
    }

    /**
     *  dependencies可能是字符串和数组，需要先转换为Map<String, Any>再进行后续处理：
     *  https://github.com/npm/normalize-package-data/blob/46967fd9b06db3cc23f6f813c89a9f6232299563/lib/fixer.js#L427
     *
     *
     * npm版本元数据的dependencies或者devDependencies对象里面，键值对的值通常是字符串类型的版本号，即
     *     "devDependencies": {
     *         <pkg>: <version>
     *         ...
     *     }
     * 存在少量的npm版本元数据，这个值是一个JSON对象，里面包含了一个key为"version"、值为字符串类型版本号的键值对，例如deep-diff@0.1.0
     *     "devDependencies": {
     *         <pkg>: {
     *             "version": <version>
     *         }
     *         ...
     *     }
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveDependencies(dependencies: Any): Map<String, Any> {
        // dependencies可能是字符串、数组或map，统一转换为Map<String, Any>
        val depsMap = when (dependencies) {
            is Map<*, *> -> dependencies as Map<String, Any>
            is String -> objectifyDepsList(dependencies.trim().split(Regex("[\n\r\\s\t ,]+")))
            is List<*> -> objectifyDepsList(dependencies.filterIsInstance<String>())
            else -> emptyMap()
        }
        if (depsMap.isEmpty()) return depsMap

        // value的类型可能是String或Map，Map类型需要转换为String
        return when (depsMap.values.first()) {
            is String -> depsMap
            is Map<*, *> -> {
                depsMap.mapValues {
                    val dependencyMap = it.value as Map<*, *>
                    dependencyMap[VERSION] ?: throw IOException(
                        "Invalid package.json format($name-$version-dependencies/devDependencies-${it.key})"
                    )
                }
            }
            else ->
                throw IOException(
                    "Invalid package.json format. The dependencies/devDependencies field cannot be parsed."
                )
        }
    }

    private fun objectifyDepsList(depsList: List<String>): Map<String, String> {
        val depsMap = mutableMapOf<String, String>()
        depsList.forEach {
            val depString = it.trim()
            if (depString.isBlank()) return@forEach
            val delimiterIndex = depString.indexOfFirst { c -> c.isWhitespace() || "@<>=".contains(c) }
            when (delimiterIndex) {
                -1 -> depsMap[depString] = ""
                0 -> {}
                else -> {
                    val name = depString.take(delimiterIndex)
                    val version = depString.drop(delimiterIndex).trim().removePrefix("@")
                    depsMap[name] = version
                }
            }
        }
        return depsMap
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Dist : Serializable {
        @JsonProperty("tarball")
        var tarball: String? = null

        @JsonProperty("shasum")
        var shasum: String? = null

        private var other: MutableMap<String, Any?> = mutableMapOf()

        @JsonAnySetter
        fun set(name: String, value: Any?) {
            this.other[name] = value
        }

        @JsonAnyGetter
        fun any(): Map<String, Any?> {
            return this.other
        }
    }
}
