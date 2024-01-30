/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.constant.FORBID_STATUS
import com.tencent.bkrepo.common.artifact.constant.FORBID_TYPE
import com.tencent.bkrepo.common.artifact.constant.FORBID_USER
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_FULL_PATH
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_PROJECT
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_REPO
import com.tencent.bkrepo.common.artifact.constant.SCAN_STATUS
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.pojo.metadata.ForbidType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel

/**
 * 元数据工具类
 */
object MetadataUtils {
    /**
     * 元数据KEY保留字，仅允许系统使用
     */
    private val RESERVED_KEY = setOf(
        SCAN_STATUS,
        FORBID_STATUS,
        FORBID_USER,
        FORBID_TYPE,
        METADATA_KEY_LINK_PROJECT,
        METADATA_KEY_LINK_REPO,
        METADATA_KEY_LINK_FULL_PATH,
    )

    /**
     * 用于兼容旧逻辑，优先从[metadataModels]取数据，[metadataModels]不存在时从[metadataMap]取
     */
    fun compatibleConvertAndCheck(
        metadataMap: Map<String, Any>?,
        metadataModels: List<MetadataModel>?
    ): MutableList<TMetadata> {
        return if (!metadataModels.isNullOrEmpty()) {
            metadataModels.map { convertAndCheck(it) }.toMutableList()
        } else {
            convertAndCheck(metadataMap)
        }
    }

    fun toMap(metadataList: List<TMetadata>?): Map<String, Any> {
        return metadataList?.associate { it.key to it.value }.orEmpty()
    }

    fun toList(metadataList: List<TMetadata>?): List<MetadataModel> {
        return metadataList?.map {
            MetadataModel(
                key = it.key,
                value = it.value,
                system = it.system,
                description = it.description,
                link = it.link
            )
        }.orEmpty()
    }

    /**
     * 合并[oldMetadata]与[newMetadata]，存在相同的key时[newMetadata]的项会替换[oldMetadata]的元数据项
     */
    fun merge(
        oldMetadata: List<TMetadata>,
        newMetadata: List<TMetadata>
    ): MutableList<TMetadata> {
        val metadataMap = oldMetadata.associateByTo(HashMap(oldMetadata.size + newMetadata.size)) { it.key }
        newMetadata.forEach { metadataMap[it.key] = it }
        return metadataMap.values.toMutableList()
    }

    fun extractForbidMetadata(
        metadata: List<MetadataModel>,
        operator: String = SecurityUtils.getUserId()
    ): MutableList<MetadataModel>? {
        val forbidMetadata = metadata.firstOrNull {
            it.key == FORBID_STATUS && it.value is Boolean
        } ?: return null
        val result = ArrayList<MetadataModel>(3)

        result.add(forbidMetadata.copy(system = true))
        // 添加禁用操作用户和类型
        result.add(MetadataModel(key = FORBID_USER, value = operator, system = true))
        result.add(MetadataModel(key = FORBID_TYPE, value = ForbidType.MANUAL.name, system = true))

        return result
    }

    fun convert(metadataList: List<Map<String, Any>>): Map<String, Any> {
        return metadataList.filter { it.containsKey("key") && it.containsKey("value") }
            .associate { it.getValue("key").toString() to it.getValue("value") }
    }

    fun convertToMetadataModel(metadataList: List<Map<String, Any>>): List<MetadataModel> {
        return metadataList.filter { it.containsKey("key") && it.containsKey("value") }
            .map {
                val key = it.getValue(TMetadata::key.name).toString()
                val value = it.getValue(TMetadata::value.name)
                val system = it[TMetadata::system.name] as Boolean? ?: false
                val description = it[TMetadata::description.name]?.toString()
                val link = it[TMetadata::link.name]?.toString()
                MetadataModel(key = key, value = value, system = system, description = description, link = link)
            }
    }

    fun convertAndCheck(metadata: MetadataModel): TMetadata {
        with(metadata) {
            val tMetadata = TMetadata(
                key = key,
                value = value,
                system = system,
                description = description,
                link = link
            )
            checkReservedKey(key, system)
            return tMetadata
        }
    }

    /**
     * 将允许用户新增为系统元数据的元数据设置为System=true
     */
    fun changeSystem(nodeMetadata: List<MetadataModel>?, allowUserAddSystemMetadata: List<String>) =
        nodeMetadata?.map { m ->
            if (allowUserAddSystemMetadata.any { it.equals(m.key, true) }) {
                m.copy(system = true)
            } else {
                m
            }
        }?.toMutableList()

    private fun convertAndCheck(metadataMap: Map<String, Any>?): MutableList<TMetadata> {
        return metadataMap
            ?.filter { it.key.isNotBlank() }
            .orEmpty()
            .map {
                val tMetadata = TMetadata(key = it.key, value = it.value)
                checkReservedKey(tMetadata.key)
                tMetadata
            }
            .toMutableList()
    }

    private fun checkReservedKey(key: String, system: Boolean = false) {
        if (key in RESERVED_KEY && !system) {
            throw ErrorCodeException(RepositoryMessageCode.METADATA_KEY_RESERVED, key)
        }
    }
}
