package com.tencent.bkrepo.auth.service.local

import com.mongodb.BasicDBObject
import com.tencent.bkrepo.auth.constant.DEFAULT_PASSWORD
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
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
        val user = userRepository.findOneByUserId(request.userId)
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
                roles = emptyList()
            )
        )
        return true
    }

    override fun deleteById(userId: String): Boolean {
        val user = userRepository.findOneByUserId(userId)
        if (user == null) {
            logger.warn("user [$userId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        userRepository.findOneByUserId(userId)
        return true
    }

    override fun addUserToRole(userId: String, roleId: String): User? {
        // check user
        val user = userRepository.findOneByUserId(userId)
        if (user == null) {
            logger.warn(" user not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        // check role
        val role = roleRepository.findOneById(roleId)
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
        IdList.forEach {
            // check user
            val user = userRepository.findOneByUserId(it)
            if (user == null) {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }

        // check role
        val role = roleRepository.findOneById(roleId)
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
        // check user
        val user = userRepository.findOneByUserId(userId)
        if (user == null) {
            logger.warn(" user not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        // check role
        val role = roleRepository.findOneById(roleId)
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
        IdList.forEach {
            val user = userRepository.findOneByUserId(it)
            if (user == null) {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }

        // check role
        val role = roleRepository.findOneById(roleId)
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
        val user = userRepository.findOneByUserId(userId)
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
        val user = userRepository.findOneByUserId(userId)
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

    override fun removeToken(userId: String, token: String): User? {
        val user = userRepository.findOneByUserId(userId)
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
        val user = userRepository.findOneByUserId(userId) ?: return null
        return User(
            userId = user.userId!!,
            name = user.name,
            pwd = "",
            admin = user.admin,
            locked = user.locked,
            tokens = user.tokens,
            roles = user.roles
        )
    }

    override fun findUserByUserToken(userId: String, pwd: String): User? {
        val hashPwd = DataDigestUtils.md5FromStr(pwd)
        val criteria = Criteria()
        criteria.orOperator(Criteria.where(TUser::pwd.name).`is`(hashPwd), Criteria.where("tokens.id").`is`(pwd)).and(TUser::userId.name).`is`(userId)
        val query = Query.query(criteria)
        val result = mongoTemplate.findOne(query, TUser::class.java) ?: return null
        return User(
            userId = result.userId!!,
            name = result.name,
            pwd = "",
            admin = result.admin,
            locked = result.locked,
            tokens = result.tokens,
            roles = result.roles
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserServiceImpl::class.java)
    }
}
