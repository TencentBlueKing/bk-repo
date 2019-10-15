package com.tencent.bkrepo.docker.manifest2

class Descriptor() {
    var platform: Platform = Platform()
    var mediaType: String = ""
    var size: Int = 0
    var digest: String = ""
    var urls: List<String> = emptyList()
    val annotations: Map<String, String> = emptyMap()
}
