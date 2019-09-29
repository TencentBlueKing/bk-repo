package com.tencent.bkrepo.registry.manifest

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.storage.util.DataDigestUtils

// deserialize manifest data
class ManifestDeserializer() {

    companion object {
        fun deserialize(manifestType: String, manifestBytes: ByteArray): Descriptor {
            val des = Descriptor()
            when (manifestType) {
                ManifestType.valueOf("Schema1").getType() -> {

                    //                    return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                }
                ManifestType.valueOf("Schema1Signed").getType(), "", "application/json" -> {
                    var V2Desc = Descriptor()
                    val v1Desc = JsonUtils.getObjectMapper().readValue(manifestBytes, ManifestV1::class.java)
                    if (v1Desc.mediaType != ManifestType.valueOf("Schema1Signed").getType()) {
                        return des
                    }
                    var v1DescByteArray = JsonUtils.getObjectMapper().writeValueAsBytes(v1Desc)
                    V2Desc.digest = DataDigestUtils.sha256FromByteArray(v1DescByteArray)
                    V2Desc.size = v1DescByteArray.count()
                    V2Desc.mediaType = ManifestType.valueOf("Schema1Signed").getType()
                    return V2Desc
                }
                ManifestType.valueOf("Schema2List").getType() -> {
                    var V2Desc = Descriptor()
                    val v2ListDesc = JsonUtils.getObjectMapper().readValue(manifestBytes, ManifestDescriptor::class.java)
                    if (v2ListDesc.mediaType != ManifestType.valueOf("Schema2List").getType()) {
                        return des
                    }
                    V2Desc.digest = DataDigestUtils.sha256FromByteArray(manifestBytes)
                    V2Desc.size = manifestBytes.count()
                    V2Desc.mediaType = ManifestType.valueOf("Schema2List").getType()
                    return V2Desc
                }
                ManifestType.valueOf("Schema2").getType() -> {
                    val v2Desc = JsonUtils.getObjectMapper().readValue(manifestBytes, Descriptor::class.java)
                    if (v2Desc.mediaType != ManifestType.valueOf("Schema2").getType()) {
                        return des
                    }
                    v2Desc.digest = DataDigestUtils.sha256FromByteArray(manifestBytes)
                    v2Desc.size = manifestBytes.count()
                    v2Desc.mediaType = ManifestType.valueOf("Schema2List").getType()
                    return v2Desc
                }
                else -> {
                    return des
                }
            }
            return des
            }
    }
}
