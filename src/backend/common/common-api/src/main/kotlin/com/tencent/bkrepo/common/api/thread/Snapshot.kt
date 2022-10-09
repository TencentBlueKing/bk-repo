package com.tencent.bkrepo.common.api.thread

/**
 * 用于传播threadLocal值的快照
 * */
data class Snapshot(val threadLocal2Value: MutableMap<ThreadLocal<Any>, Any>)
