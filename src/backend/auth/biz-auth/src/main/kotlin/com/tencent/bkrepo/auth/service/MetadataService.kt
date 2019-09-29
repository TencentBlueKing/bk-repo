package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.auth.model.TMetadata
import com.tencent.bkrepo.auth.pojo.Metadata
import com.tencent.bkrepo.auth.pojo.MetadataDeleteRequest
import com.tencent.bkrepo.auth.pojo.MetadataUpsertRequest
import com.tencent.bkrepo.auth.repository.MetadataRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * 元数据service
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
@Service
class MetadataService @Autowired constructor(
    private val metadataRepository: MetadataRepository,
    private val mongoTemplate: MongoTemplate
) {
    fun getDetailById(id: String): Metadata {
        return toNode(metadataRepository.findByIdOrNull(id)) ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND)
    }

    fun list(nodeId: String): List<Metadata> {
        return metadataRepository.findByNodeId(nodeId)
    }

    fun upsert(metadataUpsertRequest: MetadataUpsertRequest) {
        with(metadataUpsertRequest) {
            this.dataMap.forEach {
                val update = Update.update("value", LocalDateTime.now())
                        .set("lastModifiedDate", LocalDateTime.now())
                        .set("lastModifiedBy", this.operateBy)
                        .setOnInsert("createdDate", LocalDateTime.now())
                        .setOnInsert("createdBy", this.operateBy)
                mongoTemplate.upsert(Query(Criteria.where("nodeId").`is`(this.nodeId).and("key").`is`(it.key)), update, TMetadata::class.java)
            }
        }
    }

    fun delete(metadataDeleteRequest: MetadataDeleteRequest) {
        metadataDeleteRequest.run {
            if (this.deleteAll) {
                metadataRepository.deleteByNodeId(this.nodeId)
            } else {
                this.metadataIdList?.let { metadataRepository.deleteByIdIn(it) }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MetadataService::class.java)

        fun toNode(tMetadata: TMetadata?): Metadata? {
            return tMetadata?.let { Metadata(
                    it.id!!,
                    it.key,
                    it.value,
                    it.nodeId
                )
            }
        }
    }
}
