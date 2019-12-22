package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER

class UserUtil {
    companion object {
        fun getContextUserId (id:String?):String{
            if (id == null) {
                return ANONYMOUS_USER
            }
            return id
        }
    }
}