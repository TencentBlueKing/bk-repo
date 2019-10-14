package com.tencent.bkrepo.registry.manifest2

import com.fasterxml.jackson.annotation.JsonProperty

class PlatformSpec() {
    var architecture: String = ""
    var os: String = ""
    @JsonProperty("os.version")
    var osVersion: String = ""
    @JsonProperty("os.features")
    var osFeatures: List<String> = emptyList()
    var variant: String = ""
    var features: List<String> = emptyList()
}
