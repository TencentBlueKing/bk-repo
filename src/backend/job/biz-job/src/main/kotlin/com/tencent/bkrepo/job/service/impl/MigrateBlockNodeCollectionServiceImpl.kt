package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.mongo.api.util.MongoDaoHelper
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.service.MigrateBlockNodeCollectionService
import org.apache.commons.lang3.reflect.FieldUtils
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.lang.reflect.Field
import java.util.function.Consumer

@Service
class MigrateBlockNodeCollectionServiceImpl(
    private val mongoTemplate: MongoTemplate,
) : MigrateBlockNodeCollectionService {

    /**
     * 迁移block_node_x表数据，目前仅支持停机迁移，且由于新数据只写入到新表，有问题时无法回滚
     *
     * @param oldCollectionNamePrefix 旧表名前缀
     * @param newCollectionNamePrefix 新表名前缀
     * @param newShardingColumns 新的分表键
     * @param newShardingCount 新分表数
     */
    override fun migrate(
        oldCollectionNamePrefix: String,
        newCollectionNamePrefix: String,
        newShardingColumns: List<String>,
        newShardingCount: Int
    ) {
        logger.info("start migrate block node")
        val startIds = getStartObjectIds(oldCollectionNamePrefix, newCollectionNamePrefix, newShardingCount)
        val newShardingFields = MongoDaoHelper.determineShardingFields(TBlockNode::class.java, newShardingColumns)
        for (i in 0 until SHARDING_COUNT) {
            val oldCollectionName = "${oldCollectionNamePrefix}_$i"
            val startId = startIds[oldCollectionName] ?: ObjectId(MIN_OBJECT_ID)
            doMigrate(oldCollectionName, startId, newCollectionNamePrefix, newShardingFields, newShardingCount)
            doValidate(oldCollectionName, newCollectionNamePrefix, newShardingFields, newShardingCount)
        }
        logger.info("migrate block node finished")
    }

    /**
     * 数据迁移
     *
     * @param oldCollection 旧表名
     * @param startId 从哪个ID开始迁移，不包含该ID对应的数据
     * @param newCollectionNamePrefix 新表名
     * @param newShardingFields 新的分表键对应的field
     * @param newShardingCount 新分表数
     */
    private fun doMigrate(
        oldCollection: String,
        startId: ObjectId,
        newCollectionNamePrefix: String,
        newShardingFields: LinkedHashMap<String, Field>,
        newShardingCount: Int,
    ) {
        logger.info("start migrate collection[$oldCollection]")
        var count = 0
        val start = System.currentTimeMillis()
        iterateCollection<TBlockNode>(Query(), oldCollection, startId, BATCH_SIZE) {
            count++
            val newCollection = newCollectionName(newCollectionNamePrefix, newShardingFields, newShardingCount, it)
            if (!mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(it.id)), newCollection)) {
                mongoTemplate.insert(it, newCollection)
            }
        }
        val elapsed = System.currentTimeMillis() - start
        logger.info("migrate collection[$oldCollection] finished, total count[$count], elapsed $elapsed ms")
    }

    /**
     * 数据校验
     *
     * @param oldCollection 旧表名
     * @param newCollectionNamePrefix 新表名
     * @param newShardingFields 新的分表键对应的field
     * @param newShardingCount 新分表数
     */
    private fun doValidate(
        oldCollection: String,
        newCollectionNamePrefix: String,
        newShardingFields: LinkedHashMap<String, Field>,
        newShardingCount: Int,
    ) {
        var oldCount = 0
        val start = System.currentTimeMillis()
        iterateCollection<TBlockNode>(Query(), oldCollection, ObjectId(MIN_OBJECT_ID), BATCH_SIZE) { old ->
            oldCount++
            val newCollection = newCollectionName(newCollectionNamePrefix, newShardingFields, newShardingCount, old)
            val query = Query.query(Criteria.where(ID).isEqualTo(old.id))
            val new = mongoTemplate.findOne(query, TBlockNode::class.java, newCollection)
            // 数据在新表中有而旧表中没有的情况暂不处理，避免遍历新表导致耗时过久，迁移过程中需停止BlockNode删除任务
            if (new == null) {
                logger.info("block node[${old.id}] missed, will be inserted, collection[$newCollection]")
                mongoTemplate.save(old, newCollection)
            } else if (new != old) {
                logger.info("block node[${old.id}] changed, will be updated, collection[$newCollection]")
                mongoTemplate.save(old, newCollection)
            }
        }
        val elapsed = System.currentTimeMillis() - start
        logger.info("validate old collection[$oldCollection] finished, total count[$oldCount], elapsed $elapsed ms")
    }

    private inline fun <reified T> iterateCollection(
        query: Query,
        collection: String,
        startId: ObjectId,
        batchSize: Int = BATCH_SIZE,
        consumer: Consumer<T>,
    ) {
        var lastId = startId
        var querySize: Int
        val idField = T::class.java.getDeclaredField("id")
        do {
            val newQuery = Query.of(query)
                .addCriteria(Criteria.where(ID).gt(lastId))
                .limit(batchSize)
                .with(Sort.by(ID).ascending())
            val data = mongoTemplate.find(newQuery, T::class.java, collection)
            if (data.isEmpty()) {
                break
            }
            data.forEach { consumer.accept(it) }
            querySize = data.size
            lastId = ObjectId(FieldUtils.readField(idField, data.last(), true) as String)
        } while (querySize == batchSize)
    }

    /**
     * 获取迁移开始位置
     *
     * @param oldCollectionNamePrefix 旧表明前缀
     * @param newCollectionNamePrefix 新表名前缀
     */
    private fun getStartObjectIds(
        oldCollectionNamePrefix: String,
        newCollectionNamePrefix: String,
        newShardingCount: Int,
    ): Map<String, ObjectId> {
        val start = System.currentTimeMillis()
        val oldCollectionStartIdMap = HashMap<String, ObjectId>(newShardingCount)
        for (i in 0 until newShardingCount) {
            val newCollection = "${newCollectionNamePrefix}_$i"
            // 取前10个，尽量获取到每个old collection迁移的起始id
            val query = Query().with(Sort.by(ID).descending()).limit(10)
            query.fields().include(ID, TBlockNode::repoName.name)
            mongoTemplate.find<Map<String, Any?>>(query, newCollection).forEach { migratedBlockNode ->
                val oldCollection = oldCollectionName(oldCollectionNamePrefix, migratedBlockNode)
                val migratedBlockId = migratedBlockNode[ID] as ObjectId
                val currentId = oldCollectionStartIdMap[oldCollection]
                if (currentId == null || currentId < migratedBlockId) {
                    if (mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(migratedBlockId)), oldCollection)) {
                        oldCollectionStartIdMap[oldCollection] = migratedBlockId
                    }
                }
            }
        }
        logger.info("get all start ids elapsed ${System.currentTimeMillis() - start}ms")
        return oldCollectionStartIdMap
    }

    private fun newCollectionName(
        newCollectionNamePrefix: String,
        newShardingFields: LinkedHashMap<String, Field>,
        newShardingCount: Int,
        data: TBlockNode
    ): String {
        val shardingValues = MongoDaoHelper.shardingValues(data, newShardingFields)
        val newShardingSequence = HashShardingUtils.shardingSequenceFor(shardingValues, newShardingCount)
        return "${newCollectionNamePrefix}_$newShardingSequence"
    }

    private fun oldCollectionName(oldCollectionNamePrefix: String, data: Map<String, Any?>): String {
        val sequence = HashShardingUtils.shardingSequenceFor(data[TBlockNode::repoName.name] as String, SHARDING_COUNT)
        return "${oldCollectionNamePrefix}_$sequence"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateBlockNodeCollectionServiceImpl::class.java)
        private const val BATCH_SIZE = 100
    }
}
