package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.media.TYPE_AUDIO_DATA

/**
 * 音频数据
 * */
class AudioData(
    private var data: ByteArray,
    private var ts: Long,
) : StreamPacket {
    override fun getDataType(): Byte {
        return TYPE_AUDIO_DATA
    }

    override fun getTimestamp(): Long {
        return ts
    }

    override fun getData(): ByteArray {
        return data
    }
}
