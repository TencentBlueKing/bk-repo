package com.tencent.bkrepo.docker.common

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class test @Autowired constructor() {
    val tt: String = "tttttttttttt"

    fun Printlines() {
        print(tt)
    }
}
