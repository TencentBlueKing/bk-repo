package com.tencent.bkrepo.archive.utils

import com.tencent.bkrepo.archive.model.IdEntity
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import org.bson.types.ObjectId
import org.reactivestreams.Publisher
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono

@Component
class ReactiveDaoUtils(reactiveMongoTemplate: ReactiveMongoTemplate) {
    init {
        Companion.reactiveMongoTemplate = reactiveMongoTemplate
    }

    companion object {
        lateinit var reactiveMongoTemplate: ReactiveMongoTemplate
        fun <T : IdEntity> query(query: Query, clazz: Class<T>): Flux<T> {
            val idQueryCursor = IdQueryCursor(query, reactiveMongoTemplate, clazz)
            return Flux.create { recurseCursor(idQueryCursor, it) }
        }

        private fun <T : IdEntity> recurseCursor(idQueryCursor: IdQueryCursor<T>, sink: FluxSink<T>) {
            Mono.from(idQueryCursor.next())
                .doOnSuccess { results ->
                    results.forEach { sink.next(it) }
                    if (idQueryCursor.hasNext) {
                        recurseCursor(idQueryCursor, sink)
                    } else {
                        sink.complete()
                    }
                }.subscribe()
        }
    }

    /**
     * id查询游标，将查询使用id在进行分页查找，避免大表skip，导致性能下降
     * */
    private class IdQueryCursor<T : IdEntity>(
        val query: Query,
        val reactiveMongoTemplate: ReactiveMongoTemplate,
        val clazz: Class<T>,
    ) {
        private var lastId = MIN_OBJECT_ID
        var hasNext: Boolean = true

        fun next(): Publisher<List<T>> {
            val idQuery = Query.of(query).addCriteria(Criteria.where(ID).gt(ObjectId(lastId)))
                .with(Sort.by(ID).ascending())
            return reactiveMongoTemplate.find(idQuery, clazz).collectList()
                .doOnSuccess {
                    if (it.isNotEmpty()) {
                        lastId = it.last().id!!
                    }
                    hasNext = it.size == query.limit
                }
        }
    }
}
