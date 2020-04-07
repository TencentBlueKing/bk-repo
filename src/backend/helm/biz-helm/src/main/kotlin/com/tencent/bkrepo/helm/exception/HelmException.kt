package com.tencent.bkrepo.helm.exception

open class HelmException(override val message: String) : RuntimeException(message)
