package com.tencent.bkrepo.registry.repomd

interface WorkContext {
    fun getContextPath(): String

    // TODO : repo
//    val getRepo: Repo

//    val getSubject: Any
//
//    val getContextMap: Map<String, Any>
//
//    val getTempDirectory: Path
//
//    fun setSystem()
//
//    fun unsetSystem()
}
