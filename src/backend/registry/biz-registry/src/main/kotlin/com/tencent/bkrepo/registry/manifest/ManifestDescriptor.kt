package com.tencent.bkrepo.registry.manifest

class ManifestDescriptor() {
    var mediaType: String = ""
    var size: Int = 0
    var digest: String = ""
    var urls: List<String> = emptyList()
    var annotations: Map<String, String> = emptyMap()
    var platform: PlatformSpec = PlatformSpec()
}
