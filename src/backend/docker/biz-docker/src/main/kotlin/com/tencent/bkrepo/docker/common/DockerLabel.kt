package com.tencent.bkrepo.docker.common

/**
 * model to describe docker label
 * @author: owenlxu
 * @date: 2019-11-12
 */
class DockerLabel {
    var key: String? = null
    var value: String? = null

    constructor(key: String, value: String) {
        this.key = key
        this.value = value
    }
}
