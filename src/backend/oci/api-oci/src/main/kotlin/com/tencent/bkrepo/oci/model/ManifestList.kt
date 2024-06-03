package com.tencent.bkrepo.oci.model

class ManifestList(
    override var schemaVersion: Int,
    var mediaType: String,
    var manifests: List<ManifestDescriptor>,
) : SchemaVersion(schemaVersion)
