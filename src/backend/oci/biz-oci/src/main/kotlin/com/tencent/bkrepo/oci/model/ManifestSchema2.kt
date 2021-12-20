package com.tencent.bkrepo.oci.model

/**
 * manifest描述文件
 */
data class ManifestSchema2(
    var schemaVersion: Int,
    var mediaType: String? = null,
    var config: ConfigDescriptor,
    var layers: List<LayerDescriptor>,
	var annotations: Map<String, String> = emptyMap()
)
