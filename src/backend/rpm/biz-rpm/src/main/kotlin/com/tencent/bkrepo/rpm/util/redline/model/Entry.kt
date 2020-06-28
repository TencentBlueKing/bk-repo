package com.tencent.bkrepo.rpm.util.redline.model

class Entry {
    lateinit var name: String
    var flags: String? = null
    var epoch: String? = null
    var version: String? = null
    var release: String? = null
    var pre: String? = null
}
