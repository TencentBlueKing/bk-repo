package com.tencent.bkrepo.oci.model

data class ManifestDescriptor(
    override var mediaType: String,
    override var size: Long,
    override var digest: String,
    var platform: Map<String, String> = emptyMap()
):Descriptor(mediaType, size, digest)
