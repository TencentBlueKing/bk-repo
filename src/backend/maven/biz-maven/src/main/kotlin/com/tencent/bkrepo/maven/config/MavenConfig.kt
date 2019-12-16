package com.tencent.bkrepo.maven.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MavenConfig(
        @Value("\${username}")
        val username: String? = null,

        @Value("\${password}")
        val password: String? = null,

        @Value("\${tempPath}")
        val tempPath: String? = null,

        @Value("\${remote.url}")
        val remoteUrl: String? = null
){

}