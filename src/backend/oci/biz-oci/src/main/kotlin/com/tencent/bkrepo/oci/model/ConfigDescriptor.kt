package com.tencent.bkrepo.oci.model

data class ConfigDescriptor(
	override var mediaType: String,
	override var size: Int,
	override var digest: String
): Descriptor(mediaType, size, digest)
