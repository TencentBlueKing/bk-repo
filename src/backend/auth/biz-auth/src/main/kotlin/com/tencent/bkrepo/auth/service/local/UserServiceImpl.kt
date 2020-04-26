package com.tencent.bkrepo.auth.service.local

import com.mongodb.BasicDBObject
import com.tencent.bkrepo.auth.constant.DEFAULT_PASSWORD
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.Token
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.util.DataDigestUtils
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import java.time.LocalDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "local")
class UserServiceImpl @Autowired constructor(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val mongoTemplate: MongoTemplate
) : UserService {
    override fun createUser(request: CreateUserRequest): Boolean {
        // todo 校验
        logger.info("create user request : {}", request.toString())
        val user = userRepository.findFirstByUserId(request.userId)
        if (user != null) {
            logger.warn("create user [${request.userId}]  is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_UID)
        }
        var pwd: String = DataDigestUtils.md5FromStr(DEFAULT_PASSWORD)
        if (request.pwd != null) {
            pwd = DataDigestUtils.md5FromStr(request.pwd!!)
        }
        userRepository.insert(
            TUser(
                userId = request.userId,
                name = request.name,
                pwd = pwd,
                admin = request.admin,
                locked = false,
                tokens = emptyList(),
                roles = emptyList(),
                asstUsers = request.asstUsers,
                group = request.group
            )
        )
        return true
    }

    override fun createUserToProject(request: CreateUserToProjectRequest): Boolean {
        // todo 校验
        logger.info("create user to project request : {}", request.toString())
        val user = userRepository.findFirstByUserId(request.userId)

        // user not exist, create user
        if (user == null) {
            var pwd: String = DataDigestUtils.md5FromStr(DEFAULT_PASSWORD)
            if (request.pwd != null) {
                pwd = DataDigestUtils.md5FromStr(request.pwd!!)
            }
            userRepository.insert(
                TUser(
                    userId = request.userId,
                    name = request.name,
                    pwd = pwd,
                    admin = request.admin,
                    locked = false,
                    tokens = emptyList(),
                    roles = emptyList(),
                    asstUsers = request.asstUsers,
                    group = request.group
                )
            )
        }

        val query = Query()
        query.addCriteria(Criteria.where("name").`is`(request.projectId))
        val result = mongoTemplate.count(query, "project")
        if (result == 0L) {
            logger.warn("user [${request.projectId}]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_PROJECT_NOT_EXIST)
        }

        return true
    }

    override fun listUser(rids: List<String>): List<User> {
        logger.info("list user rids : {}", rids.toString())
        if (rids.isEmpty()) {
            return userRepository.findAll().map { transfer(it) }
        } else {
            return userRepository.findAllByRolesIn(rids).map { transfer(it) }
        }
    }

    override fun deleteById(userId: String): Boolean {
        logger.info("delete user userId : {}", userId)
        val user = userRepository.findFirstByUserId(userId)
        if (user == null) {
            logger.warn("user [$userId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        userRepository.findFirstByUserId(userId)
        return true
    }

    override fun addUserToRole(userId: String, roleId: String): User? {
        logger.info("add user to role userId : {}, roleId : {}", userId, roleId)
        // check user
        val user = userRepository.findFirstByUserId(userId)
        if (user == null) {
            logger.warn(" user not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        // check role
        val role = roleRepository.findFirstById(roleId)
        if (role == null) {
            logger.warn(" role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }

        val query = Query()
        val update = Update()
        query.addCriteria(Criteria.where(TUser::userId.name).`is`(userId))
        update.addToSet(TUser::roles.name, roleId)
        mongoTemplate.upsert(query, update, TUser::class.java)
        return getUserById(userId)
    }

    override fun addUserToRoleBatch(IdList: List<String>, roleId: String): Boolean {
        logger.info("add user to role batch userId : {}, roleId : {}", IdList.toString(), roleId)
        IdList.forEach {
            // check user
            val user = userRepository.findFirstByUserId(it)
            if (user == null) {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }

        // check role
        val role = roleRepository.findFirstById(roleId)
        if (role == null) {
            logger.warn(" role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }

        val query = Query()
        val update = Update()
        query.addCriteria(Criteria.where(TUser::userId.name).`in`(IdList))
        update.addToSet(TUser::roles.name, roleId)
        val result = mongoTemplate.upsert(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun removeUserFromRole(userId: String, roleId: String): User? {
        logger.info("remove user from role userId : {}, roleId : {}", userId, roleId)
        // check user
        val user = userRepository.findFirstByUserId(userId)
        if (user == null) {
            logger.warn(" user not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        // check role
        val role = roleRepository.findFirstById(roleId)
        if (role == null) {
            logger.warn(" role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }

        val query = Query()
        val update = Update()
        query.addCriteria(Criteria.where(TUser::userId.name).`is`(userId).and(TUser::roles.name).`is`(roleId))
        update.unset("roles.$")
        mongoTemplate.upsert(query, update, TUser::class.java)
        return getUserById(userId)
    }

    override fun removeUserFromRoleBatch(IdList: List<String>, roleId: String): Boolean {
        logger.info("remove user from role  batch userId : {}, roleId : {}", IdList.toString(), roleId)
        IdList.forEach {
            val user = userRepository.findFirstByUserId(it)
            if (user == null) {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }

        // check role
        val role = roleRepository.findFirstById(roleId)
        if (role == null) {
            logger.warn(" role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }

        val query = Query()
        val update = Update()
        query.addCriteria(Criteria.where(TUser::userId.name).`in`(IdList).and(TUser::roles.name).`is`(roleId))
        update.unset("roles.$")
        val result = mongoTemplate.upsert(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun updateUserById(userId: String, request: UpdateUserRequest): Boolean {
        logger.info("update user userId : {}, request : {}", userId, request.toString())
        val user = userRepository.findFirstByUserId(userId)
        if (user == null) {
            logger.warn("user [$userId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        val query = Query()
        query.addCriteria(Criteria.where(TUser::userId.name).`is`(userId))
        val update = Update()
        if (request.pwd != null) {
            val pwd = DataDigestUtils.md5FromStr(request.pwd!!)
            update.set(TUser::pwd.name, pwd)
        }
        if (request.admin != null) {
            update.set(TUser::admin.name, request.admin!!)
        }
        if (request.name != null) {
            update.set(TUser::name.name, request.name!!)
        }
        val result = mongoTemplate.updateFirst(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun createToken(userId: String): User? {
        logger.info("create token userId : {}", userId)
        val user = userRepository.findFirstByUserId(userId)
        if (user == null) {
            logger.warn("user [$userId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        val query = Query.query(Criteria.where(TUser::userId.name).`is`(userId))
        val update = Update()
        val uuid = UUID.randomUUID().toString()
        val token = Token(id = uuid, createdAt = LocalDateTime.now(), expiredAt = LocalDateTime.now().plusYears(2))
        update.addToSet(TUser::tokens.name, token)
        mongoTemplate.upsert(query, update, TUser::class.java)
        return getUserById(userId)
    }

    override fun addUserToken(userId: String, token: String): User? {
        logger.info("add user token userId : {} ,token : {}", userId, token)
        val user = userRepository.findFirstByUserId(userId)
        if (user == null) {
            logger.warn("user [$userId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        val query = Query.query(Criteria.where(TUser::userId.name).`is`(userId))
        val update = Update()
        val userToken = Token(id = token, createdAt = LocalDateTime.now(), expiredAt = LocalDateTime.now().plusYears(2))
        update.addToSet(TUser::tokens.name, userToken)
        mongoTemplate.upsert(query, update, TUser::class.java)
        return getUserById(userId)
    }

    override fun removeToken(userId: String, token: String): User? {
        logger.info("remove token userId : {} ,token : {}", userId, token)
        val user = userRepository.findFirstByUserId(userId)
        if (user == null) {
            logger.warn("user [$userId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        val query = Query.query(Criteria.where(TUser::userId.name).`is`(userId))
        val s = BasicDBObject()
        s["id"] = token
        val update = Update()
        update.pull(TUser::tokens.name, s)
        mongoTemplate.updateFirst(query, update, TUser::class.java)
        return getUserById(userId)
    }

    override fun getUserById(userId: String): User? {
        logger.info("get user userId : {} ", userId)
        val user = userRepository.findFirstByUserId(userId) ?: return null
        return transfer(user)
    }

    override fun findUserByUserToken(userId: String, pwd: String): User? {
        logger.info("find user userId : {}, pwd : {} ", userId, pwd)
        val hashPwd = DataDigestUtils.md5FromStr(pwd)
        val criteria = Criteria()
        criteria.orOperator(Criteria.where(TUser::pwd.name).`is`(hashPwd), Criteria.where("tokens.id").`is`(pwd))
            .and(TUser::userId.name).`is`(userId)
        val query = Query.query(criteria)
        val result = mongoTemplate.findOne(query, TUser::class.java) ?: return null
        return transfer(result)
    }

    private fun transfer(tUser: TUser): User {
        return User(
            userId = tUser.userId,
            name = tUser.name,
            pwd = tUser.pwd,
            admin = tUser.admin,
            locked = tUser.locked,
            tokens = tUser.tokens,
            roles = tUser.roles
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserServiceImpl::class.java)
    }
}
