package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.InitValidationCheck
import com.tencent.bkrepo.common.mongo.api.routing.InitValidationResult
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.mongodb.client.model.Sorts
import org.bson.BsonTimestamp
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class MigrationInitValidator(
    private val registry: MongoRoutingRegistry,
    private val properties: MongoMultiInstanceProperties,
    private val defaultMongoTemplate: MongoTemplate,
) {

    fun validate(ruleName: String, projectId: String): InitValidationResult {
        val checks = mutableListOf<InitValidationCheck>()
        checks += checkReplicaSetHealth(defaultMongoTemplate, "default")
        val targetInstance = registry.projectsByInstance(ruleName).entries
            .firstOrNull { projectId in it.value }?.key
        if (targetInstance != null) {
            registry.primaryTemplateByInstance(ruleName, targetInstance)?.let { tmpl ->
                checks += checkReplicaSetHealth(tmpl, targetInstance)
                checks += checkWriteConcernMajority(tmpl, targetInstance)
            }
        }
        registry.allPrimaryTemplates(ruleName).forEach { (instanceName, tmpl) ->
            if (checks.none { it.name.startsWith("writeConcern:$instanceName") }) {
                checks += checkWriteConcernMajority(tmpl, instanceName)
            }
        }
        checks += checkObjectIdSample(defaultMongoTemplate, projectId)
        checks += checkOplogWindow(defaultMongoTemplate, ruleName)
        val passed = checks.all { it.passed }
        if (!passed) {
            logger.warn("INIT validation failed for project[$projectId] rule[$ruleName]: $checks")
        }
        return InitValidationResult(passed, checks)
    }

    private fun checkReplicaSetHealth(template: MongoTemplate, label: String): InitValidationCheck {
        return runCatching {
            val status = template.db.runCommand(Document("serverStatus", 1))
            val repl = status.get("repl", Document::class.java)
            val members = repl?.getList("members", Document::class.java).orEmpty()
            val healthy = members.count { it.getString("stateStr") in HEALTHY_STATES }
            if (members.size < MIN_REPLICA_MEMBERS) {
                InitValidationCheck(
                    "replicaSet:$label",
                    false,
                    "members=${members.size} < $MIN_REPLICA_MEMBERS",
                )
            } else if (healthy < MIN_REPLICA_MEMBERS) {
                InitValidationCheck(
                    "replicaSet:$label",
                    false,
                    "healthy=$healthy/${members.size}",
                )
            } else {
                InitValidationCheck("replicaSet:$label", true)
            }
        }.getOrElse {
            InitValidationCheck("replicaSet:$label", false, it.message)
        }
    }

    private fun checkWriteConcernMajority(template: MongoTemplate, label: String): InitValidationCheck {
        return runCatching {
            val probeId = ObjectId()
            val coll = INIT_PROBE_COLLECTION
            template.insert(Document("_id", probeId), coll)
            val result = template.db.getCollection(coll).withWriteConcern(
                com.mongodb.WriteConcern.MAJORITY,
            ).deleteOne(Document("_id", probeId))
            if (result.wasAcknowledged()) {
                InitValidationCheck("writeConcern:$label", true)
            } else {
                InitValidationCheck("writeConcern:$label", false, "majority write not acknowledged")
            }
        }.getOrElse {
            InitValidationCheck("writeConcern:$label", false, it.message)
        }
    }

    private fun checkObjectIdSample(template: MongoTemplate, projectId: String): InitValidationCheck {
        return runCatching {
            val col = NodeReconciliationHelper.shardCollection(projectId)
            if (!template.collectionExists(col)) {
                return InitValidationCheck("objectIdFormat", true, "no sample collection")
            }
            val sample = template.db.getCollection(col)
                .find(Document("projectId", projectId))
                .limit(1)
                .first()
            if (sample == null) {
                InitValidationCheck("objectIdFormat", true, "no documents for project")
            } else {
                val id = sample["_id"]
                val ok = id is ObjectId
                InitValidationCheck(
                    "objectIdFormat",
                    ok,
                    if (ok) null else "_id type=${id?.javaClass?.simpleName}",
                )
            }
        }.getOrElse {
            InitValidationCheck("objectIdFormat", false, it.message)
        }
    }

    private fun checkOplogWindow(template: MongoTemplate, ruleName: String): InitValidationCheck {
        val minHours = properties.rules[ruleName]?.migration?.minOplogHours ?: DEFAULT_MIN_OPLOG_HOURS
        return runCatching {
            val oplog = template.mongoDatabaseFactory.getMongoDatabase("local")
                .getCollection("oplog.rs")
            val first = oplog.find().sort(Sorts.ascending("ts")).limit(1).first()
            val last = oplog.find().sort(Sorts.descending("ts")).limit(1).first()
            if (first == null || last == null) {
                InitValidationCheck("oplogWindow", false, "oplog.rs empty or unavailable")
            } else {
                val windowHours = (oplogTsMillis(last) - oplogTsMillis(first)) / 3_600_000.0
                if (windowHours < minHours) {
                    InitValidationCheck(
                        "oplogWindow",
                        false,
                        "oplog window ${"%.1f".format(windowHours)}h < min ${minHours}h",
                    )
                } else {
                    InitValidationCheck("oplogWindow", true)
                }
            }
        }.getOrElse {
            InitValidationCheck("oplogWindow", false, it.message)
        }
    }

    private fun oplogTsMillis(doc: Document): Long = when (val ts = doc["ts"]) {
        is BsonTimestamp -> ts.time * 1000L
        is java.util.Date -> ts.time
        else -> 0L
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrationInitValidator::class.java)
        private const val INIT_PROBE_COLLECTION = "_migration_init_probe"
        private const val MIN_REPLICA_MEMBERS = 3
        private const val DEFAULT_MIN_OPLOG_HOURS = 48
        private val HEALTHY_STATES = setOf("PRIMARY", "SECONDARY")
    }
}
