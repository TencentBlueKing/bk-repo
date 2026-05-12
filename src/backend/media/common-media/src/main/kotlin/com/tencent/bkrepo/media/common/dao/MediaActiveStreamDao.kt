package com.tencent.bkrepo.media.common.dao

import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.media.common.model.TMediaActiveStream
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MediaActiveStreamDao : SimpleMongoDao<TMediaActiveStream>() {
    fun saveOrUpdate(
        streamId: String,
        machine: String,
        serverId: String? = null,
        app: String? = null,
        vhost: String? = null,
        clientIp: String? = null,
    ): TMediaActiveStream? {
        val now = LocalDateTime.now()
        val query = Query(where(TMediaActiveStream::streamId).isEqualTo(streamId))
        val update = Update()
            .set(TMediaActiveStream::machine.name, machine)
            .set(TMediaActiveStream::serverId.name, serverId)
            .set(TMediaActiveStream::app.name, app)
            .set(TMediaActiveStream::vhost.name, vhost)
            .set(TMediaActiveStream::clientIp.name, clientIp)
            .set(TMediaActiveStream::updateTime.name, now)
            .setOnInsert(TMediaActiveStream::createdTime.name, now)
        val options = FindAndModifyOptions().upsert(true).returnNew(true)
        return findAndModify(query, update, options, TMediaActiveStream::class.java)
    }

    fun deleteByStreamId(streamId: String): DeleteResult {
        val query = Query(where(TMediaActiveStream::streamId).isEqualTo(streamId))
        return remove(query)
    }

    fun findByStreamId(streamId: String): TMediaActiveStream? {
        val query = Query(where(TMediaActiveStream::streamId).isEqualTo(streamId))
        return findOne(query)
    }
}
