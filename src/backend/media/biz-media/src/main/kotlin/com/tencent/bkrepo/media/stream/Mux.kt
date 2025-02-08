package com.tencent.bkrepo.media.stream

import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.javacpp.PointerScope
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 媒体封装
 *
 * 负责对视频进行转封装
 * */
class Mux {
    /**
     * @param input 源视频文件
     * @param output 封装后的文件
     * */
    constructor(input: File, output: File) : this() {
        this.fileName = input.absolutePath
        this.outputFile = output
    }

    /**
     * @param inputStream 视频输入流
     * @param output 封装后的文件
     * */
    constructor(inputStream: InputStream, output: File, name: String) : this() {
        this.inputStream = inputStream
        this.outputFile = output
        this.fileName = name
    }

    constructor()

    private var inputStream: InputStream? = null
    private var fileName: String = ""
    private var outputFile: File? = null
    private var pkt: AVPacket? = null
    private var ifmtCtx: AVFormatContext? = null
    private var ofmtCtx: AVFormatContext? = null
    private var avio: AVIOContext? = null
    private var running: AtomicBoolean = AtomicBoolean(false)
    private var stopFlag: AtomicBoolean = AtomicBoolean(false)
    private var writeHeader = false

    @Volatile
    var packetCount = 0

    fun start() {
        if (!running.compareAndSet(false, true)) {
            logger.info("Mux was already started")
        }
        stopFlag.set(false)
        logger.info("Start remux $fileName")
        val scope = PointerScope()
        try {
            ifmtCtx = avformat.avformat_alloc_context()
            ofmtCtx = avformat.avformat_alloc_context()
            pkt = AVPacket()
            if (inputStream != null) {
                val readCb = ReadCallback(inputStream!!)
                avio = avformat.avio_alloc_context(
                    BytePointer(avutil.av_malloc(DEFAULT_BUFFER_SIZE.toLong())),
                    DEFAULT_BUFFER_SIZE,
                    0,
                    ifmtCtx,
                    readCb,
                    null,
                    null,
                )
                ifmtCtx!!.pb(avio)
            }
            var ret = avformat.avformat_open_input(ifmtCtx, fileName, null, null)
            check(ret >= 0) { "open failed [$ret]" }
            ret = avformat.avformat_find_stream_info(ifmtCtx, null as? PointerPointer<*>)
            check(ret >= 0) { "can't find stream info [$ret]" }
            if (logger.isDebugEnabled) {
                avformat.av_dump_format(ifmtCtx, 0, fileName, 0)
            }
            val outputFilePath = outputFile!!.absolutePath
            ret = avformat.avformat_alloc_output_context2(ofmtCtx, null, null, outputFilePath)
            check(ret >= 0) { "create output ctx error [$ret]" }
            val streamMapping = mutableMapOf<Int, Int>()
            var streamIndex = 0

            for (i in 0 until ifmtCtx!!.nb_streams()) {
                val inStream = ifmtCtx!!.streams(i)
                val inCodecpar = inStream.codecpar()
                if (
                    inCodecpar.codec_type() != avutil.AVMEDIA_TYPE_AUDIO &&
                    inCodecpar.codec_type() != avutil.AVMEDIA_TYPE_VIDEO &&
                    inCodecpar.codec_type() != avutil.AVMEDIA_TYPE_SUBTITLE
                ) {
                    logger.info("copy stream failed")
                    streamMapping[i] = -1
                    continue
                }
                streamMapping[i] = streamIndex++
                val outStream = avformat.avformat_new_stream(ofmtCtx, null)
                checkNotNull(outStream) { "create output stream error" }
                check(avcodec.avcodec_parameters_copy(outStream.codecpar(), inCodecpar) >= 0) { "copy params error" }
                outStream.codecpar().codec_tag(0)
                outStream.r_frame_rate(inStream.r_frame_rate())
                outStream.avg_frame_rate(inStream.avg_frame_rate())
                outStream.time_base(inStream.time_base())
            }
            if (logger.isDebugEnabled) {
                avformat.av_dump_format(ofmtCtx, 0, outputFilePath, 1)
            }
            val ofmt = ofmtCtx!!.oformat()
            if (ofmt.flags() and avformat.AVFMT_NOFILE == 0) {
                val pb = AVIOContext(null)
                check(avformat.avio_open(pb, outputFilePath, avformat.AVIO_FLAG_WRITE) >= 0) { "could not open file" }
                ofmtCtx!!.pb(pb)
            }

            check(avformat.avformat_write_header(ofmtCtx, null as? PointerPointer<*>) >= 0) {
                "Error occurred when opening output file"
            }
            writeHeader = true
            var dts = 0L
            while (!stopFlag.get()) {
                if (avformat.av_read_frame(ifmtCtx, pkt) < 0) {
                    break
                }
                val inStream = ifmtCtx!!.streams(pkt!!.stream_index())
                if (pkt!!.stream_index() >= streamMapping.size || streamMapping[pkt!!.stream_index()]!! < 0) {
                    avcodec.av_packet_unref(pkt)
                    continue
                }
                pkt!!.stream_index(streamMapping[pkt!!.stream_index()]!!)
                val outStream = ofmtCtx!!.streams(pkt!!.stream_index())
                logPacket(ifmtCtx, pkt!!, "in")
                avcodec.av_packet_rescale_ts(pkt, inStream.time_base(), outStream.time_base())
                pkt!!.time_base(inStream.time_base())
                if (pkt!!.dts() == AV_NOPTS_VALUE) {
                    pkt!!.dts(dts)
                    dts += pkt!!.duration()
                }
                pkt!!.pos(-1)
                logPacket(ofmtCtx, pkt!!, "out")
                check(avformat.av_interleaved_write_frame(ofmtCtx, pkt) >= 0) { "write frame error" }
                packetCount++
            }
            logger.info("Complete remux $fileName,size ${outputFile!!.length()} B,$packetCount packet.")
        } catch (e: Exception) {
            logger.error("Remux error:", e)
            throw e
        } finally {
            release()
            scope.close()
            running.set(false)
            logger.info("Finish remux $fileName to ${outputFile!!.absolutePath}")
        }
    }

    /**
     * 释放相关资源
     * */
    private fun release() {
        if (pkt != null) {
            if (pkt!!.stream_index() != -1) {
                avcodec.av_packet_unref(pkt)
            }
            pkt!!.releaseReference()
            pkt = null
        }

        if (inputStream == null && ifmtCtx != null && !ifmtCtx!!.isNull) {
            avformat.avformat_close_input(ifmtCtx)
            ifmtCtx = null
        }

        if (inputStream != null) {
            inputStream!!.close()
            if (avio != null) {
                if (avio!!.buffer() != null) {
                    avutil.av_free(avio!!.buffer())
                    avio!!.buffer(null)
                }
                avio = null
            }
            if (ifmtCtx != null) {
                avformat.avformat_free_context(ifmtCtx)
            }
            ifmtCtx = null
        }

        if (ofmtCtx != null) {
            if (writeHeader) {
                avformat.av_write_trailer(ofmtCtx)
            }
            val oformat = ofmtCtx!!.oformat()
            if (oformat != null) {
                if (oformat.flags() and avformat.AVFMT_NOFILE == 0 && !ofmtCtx!!.pb().isNull) {
                    avformat.avio_closep(ofmtCtx!!.pb())
                }
            }
            avformat.avformat_free_context(ofmtCtx)
            ofmtCtx = null
        }
    }

    /**
     * 停止封装
     * 会阻塞到封装停止
     * */
    fun stop() {
        logger.info("Stop mux")
        if (!stopFlag.compareAndSet(false, true)) {
            logger.info("Mux was already stopped")
            return
        }
        if (running.get()) {
            logger.info("Mux is running now,waiting...")
            while (running.get()) {
                // wait mux process stopped
            }
        }
    }

    /**
     * 记录包信息
     * */
    fun logPacket(fmtCtx: AVFormatContext? = null, packet: AVPacket, tag: String) {
        with(packet) {
            val ptsTime = String(
                avutil.av_ts_make_time_string(
                    ByteArray(avutil.AV_TS_MAX_STRING_SIZE),
                    pts(),
                    time_base(),
                ),
            )
            val dtsTime = String(
                avutil.av_ts_make_time_string(
                    ByteArray(avutil.AV_TS_MAX_STRING_SIZE),
                    dts(),
                    time_base(),
                ),
            )
            val durationTime = String(
                avutil.av_ts_make_time_string(
                    ByteArray(avutil.AV_TS_MAX_STRING_SIZE),
                    duration(),
                    time_base(),
                ),
            )
            logger.debug(
                "$tag: pts:${pts()} pts_time:$ptsTime dts:${dts()} dts_time:$dtsTime duration:${duration()}" +
                    " duration_time:$durationTime time_base:${time_base().num()},${time_base().den()}," +
                    "size:${size()} stream_index:${stream_index()}",
            )
        }
    }

    class ReadCallback(private val inputStream: InputStream) : Read_packet_Pointer_BytePointer_int() {
        override fun call(opaque: Pointer, buf: BytePointer, buf_size: Int): Int {
            val b = ByteArray(buf_size)
            val size = inputStream.read(b)
            return if (size < 0) {
                logger.info("input end")
                avutil.AVERROR_EOF
            } else {
                buf.put(b, 0, size)
                size
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Mux::class.java)
        private const val DEFAULT_BUFFER_SIZE = 4096
    }
}
