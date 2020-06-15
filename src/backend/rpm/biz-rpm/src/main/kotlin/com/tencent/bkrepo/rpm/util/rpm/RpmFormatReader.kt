package com.tencent.bkrepo.rpm.util.rpm

import com.tencent.bkrepo.rpm.util.redline.model.FormatWithType
import com.tencent.bkrepo.rpm.util.redline.model.RpmFormat
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.redline_rpm.ReadableChannelWrapper
import org.redline_rpm.header.RpmType
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel

object RpmFormatReader {
    @Throws(IOException::class)
    fun wrapStreamAndRead(sourceStream: InputStream): RpmFormat {
        Channels.newChannel(sourceStream).use { channelIn ->
            val format = getRpmFormat(channelIn)
            try {
                IOUtils.copy(sourceStream, NullOutputStream())
            } catch (e: Exception) {
                // TODO
            }
            return format
        }
    }

    @Throws(IOException::class)
    fun getRpmFormat(channel: ReadableByteChannel): RpmFormat {
        val format = FormatWithType()
        val readableChannelWrapper = ReadableChannelWrapper(channel)
        val headerStartKey = readableChannelWrapper.start()

        format.lead.read(readableChannelWrapper)

        val headerStartPos = readableChannelWrapper.finish(headerStartKey) as Int
        format.header.startPos = headerStartPos
        val headerKey = readableChannelWrapper.start()
        val headerLength = readableChannelWrapper.finish(headerKey) as Int
        format.header.endPos = headerStartPos + headerLength

        return RpmFormat(headerStartPos, headerStartPos + headerLength, format, RpmType.BINARY)
    }
}
