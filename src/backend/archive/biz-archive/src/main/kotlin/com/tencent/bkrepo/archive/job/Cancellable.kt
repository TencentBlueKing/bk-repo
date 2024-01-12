package com.tencent.bkrepo.archive.job

/**
 * 可取消的操作
 * */
interface Cancellable {
    /**
     * 取消
     * */
    fun cancel()
}
