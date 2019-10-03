package com.tencent.bkrepo.registry.common

import kotlin.collections.Map.Entry

class DockerLabel {
    var key: String? = null
        private set
    var value: String? = null
        private set

    constructor(key: String, value: String) {
        this.key = key
        this.value = value
    }

    constructor(dockerV2InfoLabelEntry: Entry<String, String>) {
        this.key = dockerV2InfoLabelEntry.key
        this.value = dockerV2InfoLabelEntry.value
    }
}
