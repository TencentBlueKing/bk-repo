package com.tencent.bkrepo.helm


class Chart() {
    // val apiVersion: String?,
    // val name: String?,
    // val description: String?,
    // val type: String?,
    // val version: String?,
    // val appVersion: String?,
    // val created: String?,
    // val urls: List<String>?,
    // val digest: String?

    private val apiVersion: String = ""
    private val name: String? = null
    private val description: String? = null
    private val type: String? = null
    private val version: String? = null
    private val appVersion: String? = null
    private val created: String? = null
    private val urls: List<String>? = null
    private val digest: String? = null

    override fun toString(): String {
        return "Chart(apiVersion='$apiVersion', name=$name, description=$description, type=$type, version=$version, appVersion=$appVersion, created=$created, urls=$urls, digest=$digest)"
    }
}