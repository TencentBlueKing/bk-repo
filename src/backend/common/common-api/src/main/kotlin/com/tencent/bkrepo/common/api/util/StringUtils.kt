package com.tencent.bkrepo.common.api.util

private val alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomString(size: Int) = List(size) { alphabet.random() }.joinToString("")
