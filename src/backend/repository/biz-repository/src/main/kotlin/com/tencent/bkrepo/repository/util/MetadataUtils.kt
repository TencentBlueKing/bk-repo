package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.repository.model.TMetadata

/**
 * 元数据工具类
 */
object MetadataUtils {

    fun fromMap(metadataMap: Map<String, Any>?): MutableList<TMetadata> {
        return metadataMap?.filter { it.key.isNotBlank() }?.map { TMetadata(it.key, it.value) }.orEmpty().toMutableList()
    }

    fun toMap(metadataList: List<TMetadata>?): Map<String, Any> {
        return metadataList?.map { it.key to it.value }?.toMap().orEmpty()
    }
}