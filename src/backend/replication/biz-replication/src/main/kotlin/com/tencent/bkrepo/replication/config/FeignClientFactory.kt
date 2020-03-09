package com.tencent.bkrepo.replication.config

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import feign.Contract
import feign.Feign
import feign.Logger
import feign.Request
import feign.Retryer
import feign.codec.Decoder
import feign.codec.Encoder
import feign.codec.ErrorDecoder
import org.springframework.cloud.openfeign.FeignLoggerFactory

object FeignClientFactory {

    fun <T> create(target: Class<T>, url: String): T {
        return builder.logLevel(Logger.Level.BASIC)
            .logger(loggerFactory.create(target))
            .encoder(encoder)
            .decoder(decoder)
            .contract(contract)
            .retryer(retryer)
            .options(options)
            .errorDecoder(errorDecoder)
            .target(target, url)
    }

    private val builder = SpringContextUtils.getBean(Feign.Builder::class.java)
    private val loggerFactory = SpringContextUtils.getBean(FeignLoggerFactory::class.java)
    private val encoder = SpringContextUtils.getBean(Encoder::class.java)
    private val decoder = SpringContextUtils.getBean(Decoder::class.java)
    private val contract = SpringContextUtils.getBean(Contract::class.java)
    private val retryer = SpringContextUtils.getBean(Retryer::class.java)
    private val errorDecoder = SpringContextUtils.getBean(ErrorDecoder::class.java)
    // 设置不超时
    private val options = Request.Options(10 * 1000, 0)
}
