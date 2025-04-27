package com.tencent.bkrepo.git.context

import com.tencent.bkrepo.common.api.thread.TransmittableThreadLocal

object UserHolder {
    private val userHolder = TransmittableThreadLocal<String>()

    fun setUser(user: String) {
        userHolder.set(user)
    }

    fun getUser(): String {
        return userHolder.get()
    }

    fun reset() {
        userHolder.remove()
    }
}
