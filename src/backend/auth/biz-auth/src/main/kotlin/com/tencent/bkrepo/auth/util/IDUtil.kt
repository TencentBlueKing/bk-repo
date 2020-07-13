package com.tencent.bkrepo.auth.util

import java.util.UUID

object IDUtil {

    fun genRandomId(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
}
