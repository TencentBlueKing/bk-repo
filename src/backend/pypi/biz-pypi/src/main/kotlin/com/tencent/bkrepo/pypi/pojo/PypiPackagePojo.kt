package com.tencent.bkrepo.pypi.pojo

data class PypiPackagePojo(
    val name: String,
    val version: String
) {
    override fun toString(): String {
        return "$name/$version"
    }
}
