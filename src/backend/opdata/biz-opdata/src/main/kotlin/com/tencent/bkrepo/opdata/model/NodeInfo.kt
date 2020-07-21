package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import org.influxdb.annotation.Column
import org.influxdb.annotation.Measurement

@Measurement(name = "nodeInfo")
data class NodeInfo(
    @Column(name = "num", tag = true)
    private var num: Long,
    @Column(name = "size", tag = true)
    private var size: Long,
    @Column(name = "table", tag = true)
    private var table: String = EMPTY
)
