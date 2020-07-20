package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.common.api.util.JsonUtils

/**
 * to deserialize manifest schema2 manifest list
 * @author: owenlxu
 * @date: 2020-02-05
 */
object ManifestListSchema2Deserializer {

    fun deserialize(manifestBytes: ByteArray): ManifestListJson? {
        return JsonUtils.objectMapper.readValue(manifestBytes, ManifestListJson::class.java)
    }
}
