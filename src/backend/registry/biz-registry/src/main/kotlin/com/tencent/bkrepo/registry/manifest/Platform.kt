package com.tencent.bkrepo.registry.manifest

import com.fasterxml.jackson.annotation.JsonProperty

class Platform() {
    var architecture: String = ""
    var os: String = ""
    @JsonProperty("os.version")
    var osVersion: String = ""
    @JsonProperty("os.features")
    var osFeatures: List<String> = emptyList()
    var variant: String = ""
}
