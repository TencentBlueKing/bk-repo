package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.media.TYPE_CLIENT_MOUSE_DATA

/**
 * 鼠标数据
 * */
class MouseData(
    private var data: ByteArray,
    private var ts: Long,
) : StreamPacket {
    override fun getDataType(): Byte {
        return TYPE_CLIENT_MOUSE_DATA
    }

    override fun getTimestamp(): Long {
        return ts
    }

    override fun getData(): ByteArray {
        return data
    }
}
