package com.tencent.bkrepo.auth.service.local

import com.mongodb.BasicDBObject
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.*
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "local")
class PermissionServiceImpl @Autowired constructor(
        private val userService: UserService,
        private val permissionRepository: PermissionRepository,
        private val mongoTemplate: MongoTemplate
) : PermissionService {

    override fun deletePermission(id: String) :Boolean{
        permissionRepository.deleteById(id)
        return true
    }

    override fun listPermission(resourceType: ResourceType?, projectId: String?): List<Permission> {
        return if (resourceType == null && projectId == null) {
            return permissionRepository.findAll().map { transfer(it) }
        } else if (projectId == null && resourceType!= null) {
            return permissionRepository.findByResourceType(resourceType).map { transfer(it) }
        } else if (projectId != null && resourceType!= null) {
            return permissionRepository.findByResourceTypeAndProjectId(resourceType, projectId).map { transfer(it) }
        }else{
            return emptyList()
        }

    }

    private fun transfer(tPermission: TPermission): Permission {
        return Permission(
                id = tPermission.id,
                resourceType = tPermission.resourceType,
                projectId = tPermission.projectId,
                permName = tPermission.permName,
                repos = tPermission.repos,
                includePattern = tPermission.includePattern,
                excludePattern = tPermission.excludePattern,
                users = tPermission.users,
                roles = tPermission.roles,
                createBy = tPermission.createBy,
                createAt = tPermission.createAt,
                updatedBy = tPermission.updatedBy,
                updateAt = tPermission.updateAt
                )
    }

    override fun createPermission(request: CreatePermissionRequest) : Boolean{
        // todo check request
        val result = permissionRepository.insert(
                TPermission(
                        resourceType = request.resourceType,
                        projectId = request.projectId,
                        permName = request.permName,
                        repos = request.repos,
                        includePattern = request.includePattern,
                        excludePattern = request.excludePattern,
                        users = request.users,
                        roles = request.roles,
                        createBy = request.createBy,
                        createAt = LocalDateTime.now(),
                        updatedBy = request.updatedBy,
                        updateAt = LocalDateTime.now()
                )
        )
        if (result.id != null) {
            return true
        }
        return false
    }

    override fun updateIncludePath(id :String, path: List<String>):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id))
        val update = Update()
        update.set("includePattern",path )
        val result = mongoTemplate.upsert(query, update, TPermission::class.java)
        if (result.matchedCount == 1L){
            return true
        }
        return false
    }

    override fun updateExcludePath(id :String, path: List<String>):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id))
        val update = Update()
        update.set("excludePattern",path )
        val result = mongoTemplate.upsert(query, update, TPermission::class.java)
        if (result.matchedCount == 1L){
            return true
        }
        return false
    }

    override fun updateRepoPermission(id :String, repos: List<String>):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id))
        val update = Update()
        update.set("repos",repos )
        val result = mongoTemplate.upsert(query, update, TPermission::class.java)
        if (result.matchedCount == 1L){
            return true
        }
        return false
    }

    override fun addUserPermission(id :String, uid:String ,actions :List<PermissionAction>):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id))
        val update = Update()
        var userAction = PermissionSet(id =uid,action= actions)
        update.addToSet("users", userAction)
        val result = mongoTemplate.updateFirst(query, update, TPermission::class.java)
        if (result.matchedCount == 1L){
            return true
        }
        return false
    }

    override fun removeUserPermission(id :String, uid:String ):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id))
        val update = Update()
        val s = BasicDBObject()
        s["id"] = uid
        update.pull("users", s)
        val result = mongoTemplate.updateFirst(query, update, TPermission::class.java)
        if (result.modifiedCount == 1L){
            return true
        }
        return false
    }

    override fun updateUserPermission(id :String, uid:String,actions :List<PermissionAction> ):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id)
                .and("users.id").`is`(uid))
        val update = Update()
        update.set("users.$.action", actions)
        val result = mongoTemplate.updateFirst(query, update, TPermission::class.java)
        if (result.modifiedCount == 1L){
            return true
        }
        return false
    }

    override fun addRolePermission(id :String, rid:String ,actions :List<PermissionAction>):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id))
        val update = Update()
        var roleAction = PermissionSet(id =rid,action= actions)
        update.addToSet("roles", roleAction)
        val result = mongoTemplate.updateFirst(query, update, TPermission::class.java)
        if (result.matchedCount == 1L){
            return true
        }
        return false
    }

    override fun removeRolePermission(id :String, rid:String ):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id))
        val update = Update()
        val s = BasicDBObject()
        s["id"] = rid
        update.pull("roles", s)
        val result = mongoTemplate.updateFirst(query, update, TPermission::class.java)
        if (result.modifiedCount == 1L){
            return true
        }
        return false
    }

    override fun updateRolePermission(id :String, rid:String,actions :List<PermissionAction> ):Boolean {
        val query = Query.query(Criteria.where("_id").`is`(id)
                .and("roles.id").`is`(rid))
        val update = Update()
        update.set("roles.$.action", actions)
        val result = mongoTemplate.updateFirst(query, update, TPermission::class.java)
        if (result.modifiedCount == 1L){
            return true
        }
        return false
    }


    override fun checkPermission(request: CheckPermissionRequest): Boolean {
        val user = userService.getUserById(request.uid) ?: return false
        if (user.admin!!) return true
        val roles   = user.roles
        val criteria = Criteria()
        criteria.orOperator(Criteria.where("users._id").`is`(request.uid).and("users.action").`is`(request.action.toString()),
                Criteria.where("roles._id").`in`(roles).and("users.action").`is`(request.action.toString()))
                        .and("projectId").`is`(request.projectId)
                        .and("repos").`is`(request.repoName)
                .and("resourceType").`is`(request.resourceType.toString())
        val query = Query.query(criteria)
        val result = mongoTemplate.count(query,TPermission::class.java)
        if (result == 0L){
            return false
        }
        return true
    }


    private fun check(request: CheckPermissionRequest, permission: PermissionInstance): Boolean {
        when (request.resourceType) {
            ResourceType.PROJECT -> { // 项目管理权限，项目权限匹配 -> 通过
                if (permission.resourceType == ResourceType.PROJECT) {
                    return permission.action == PermissionAction.MANAGE || permission.action == request.action
                }
                return false
            }
            ResourceType.REPO, ResourceType.NODE -> { // 项目管理权限，项目权限匹配，仓库权限匹配 -> 通过
                if (permission.resourceType == ResourceType.PROJECT) {
                    return permission.action == PermissionAction.MANAGE || permission.action == request.action
                }
                if (permission.resourceType == ResourceType.REPO) {
                    return permission.action == request.action
                            && (permission.repoId == "*" || permission.repoId == request.repoName)
                }
                return false
            }
            else -> {
                throw RuntimeException("unsupported resource type")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionServiceImpl::class.java)
    }
}
