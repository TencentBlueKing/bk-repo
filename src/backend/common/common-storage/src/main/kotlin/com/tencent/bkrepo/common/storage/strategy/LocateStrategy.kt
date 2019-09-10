package com.tencent.bkrepo.common.storage.strategy

/**
 * 文件落地策略
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
interface LocateStrategy {

    /**
     * 根据文件hash值定位文件存储位置
     */
    fun locate(hash: String): String

}