package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.media.TYPE_VIDEO_DATA

/**
 * 视频数据
 * */
class VideoData(
    private var data: ByteArray,
    private var ts: Long,
) : StreamPacket {
    override fun getDataType(): Byte {
        return TYPE_VIDEO_DATA
    }

    override fun getTimestamp(): Long {
        return ts
    }

    override fun getData(): ByteArray {
        return data
    }
}
