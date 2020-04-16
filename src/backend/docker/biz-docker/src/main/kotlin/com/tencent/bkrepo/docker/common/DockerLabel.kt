package com.tencent.bkrepo.docker.common

class DockerLabel {
    var key: String? = null
        private set
    var value: String? = null
        private set

    constructor(key: String, value: String) {
        this.key = key
        this.value = value
    }
}
