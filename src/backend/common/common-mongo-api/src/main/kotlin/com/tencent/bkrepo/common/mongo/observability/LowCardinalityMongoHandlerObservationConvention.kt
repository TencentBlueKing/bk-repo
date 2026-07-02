package com.tencent.bkrepo.common.mongo.observability

import org.springframework.data.mongodb.observability.MongoHandlerContext
import org.springframework.data.mongodb.observability.MongoHandlerObservationConvention

/**
 * 使用固定 span name 的 MongoDB Observation convention，避免 Spring 默认的
 * `{collection}.{command}` 动态 contextual name 导致 trace 基数膨胀。
 *
 * collection、command 等动态信息仍通过低基数 attribute 传递，与
 * [org.springframework.data.mongodb.observability.DefaultMongoHandlerObservationConvention] 一致。
 */
class LowCardinalityMongoHandlerObservationConvention : MongoHandlerObservationConvention {

    override fun getContextualName(context: MongoHandlerContext): String = SPAN_NAME

    companion object {
        const val SPAN_NAME = "mongodb.command"
    }
}
