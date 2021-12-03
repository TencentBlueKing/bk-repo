package com.tencent.bkrepo.oci.model

data class LayerDescriptor(
	override var mediaType: String,
	override var size: Int,
	override var digest: String,
	var annotations: Map<String, String> = emptyMap()
): Descriptor(mediaType, size, digest)
