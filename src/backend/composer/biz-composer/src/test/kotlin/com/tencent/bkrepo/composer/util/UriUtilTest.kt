package com.tencent.bkrepo.composer.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

internal class UriUtilTest{
    @Test
    fun getFilename(uri: String) {
        val regex = "monolog"
//        val regex = "([a-zA-Z0-9])+-(\\d\\.)+\\.([zip|tar|tar.gz|tgz])"
        val pattern = Pattern.compile(regex).matcher(uri)
        println(pattern.group(0))
        println(pattern.group(1))
        println(pattern.group(2))

    }

    @Test
    fun test() {
        getFilename("monolog")
    }
}