package com.tencent.bkrepo.nuget.pojo

data class NupkgVersion(
    val id: String,
    val version: String
) {
    override fun toString(): String {
        return "$id.$version.nupkg"
    }
}
