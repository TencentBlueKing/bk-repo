package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.Token
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.storage.util.DataDigestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime
import java.util.*
import com.mongodb.BasicDBObject
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.common.api.constant.AuthMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory


@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "local")
class UserServiceImpl @Autowired constructor(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val mongoTemplate: MongoTemplate
) : UserService {
    override fun createUser(request: CreateUserRequest): Boolean {
        // todo 校验
        val user = userRepository.findOneByUId(request.uid)
        if (user != null) {
            logger.warn("create user [${request.uid}]  is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_UID)
        }
        val pwd = DataDigestUtils.md5FromStr(request.pwd)
        userRepository.insert(
            TUser(
                uId = request.uid,
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

    override fun deleteById(uId: String): Boolean {
        val user = userRepository.findOneByUId(uId)
        if (user == null) {
            logger.warn("user [$uId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        userRepository.deleteByUId(uId)
        return true
    }

    override fun addUserToRole(uId: String, rId: String): Boolean {
        //check user
        val user = userRepository.findOneByUId(uId)
        if (user == null) {
            logger.warn(" user not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        //check role
        val role = roleRepository.findOneById(rId)
        if (role == null) {
            logger.warn(" role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }

        val query = Query()
        val update = Update()
        query.addCriteria(Criteria.where("uId").`is`(uId))
        update.addToSet("roles", rId)
        val result = mongoTemplate.upsert(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun addUserToRoleBatch(IdList: List<String>, rId: String): Boolean {
        IdList.forEach{
            //check user
            val user = userRepository.findOneByUId(it)
            if (user == null) {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }

        //check role
        val role = roleRepository.findOneById(rId)
        if (role == null) {
            logger.warn(" role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }

        val query = Query()
        val update = Update()
        query.addCriteria(Criteria.where("uId").`in`(IdList))
        update.addToSet("roles", rId)
        val result = mongoTemplate.upsert(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }


    override fun removeUserFromRole(uId: String, rId: String): Boolean {
        //check user
        val user = userRepository.findOneByUId(uId)
        if (user == null) {
            logger.warn(" user not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        //check role
        val role = roleRepository.findOneById(rId)
        if (role == null) {
            logger.warn(" role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }

        val query = Query()
        val update = Update()
        query.addCriteria(Criteria.where("uId").`is`(uId).and("roles").`is`(rId))
        update.unset("roles.$")
        val result = mongoTemplate.upsert(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun removeUserFromRoleBatch(IdList: List<String>, rId: String): Boolean {
        IdList.forEach{
            val user = userRepository.findOneByUId(it)
            if (user == null) {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }

        //check role
        val role = roleRepository.findOneById(rId)
        if (role == null) {
            logger.warn(" role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }

        val query = Query()
        val update = Update()
        query.addCriteria(Criteria.where("uId").`in`(IdList).and("roles").`is`(rId))
        update.unset("roles.$")
        val result = mongoTemplate.upsert(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun updateUserById(uId: String, request: UpdateUserRequest): Boolean {
        val user = userRepository.findOneByUId(uId)
        if (user == null) {
            logger.warn("user [$uId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        val query = Query()
        query.addCriteria(Criteria.where("uId").`is`(uId))
        val update = Update()
        if (request.pwd != null) {
            val pwd = DataDigestUtils.md5FromStr(request.pwd!!)
            update.set("pwd", pwd)
        }
        if (request.admin != null) {
            update.set("admin", request.admin!!)
        }
        if (request.name != null) {
            update.set("name", request.name!!)
        }
        val result = mongoTemplate.updateFirst(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun createToken(uId: String): Boolean {
        val user = userRepository.findOneByUId(uId)
        if (user == null) {
            logger.warn("user [$uId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        val query = Query.query(Criteria.where("uId").`is`(uId))
        val update = Update()
        val uuid = UUID.randomUUID().toString()
        val token = Token(id = uuid, createdAt = LocalDateTime.now(), expiredAt = LocalDateTime.now().plusYears(2))
        update.addToSet("tokens", token)
        val result = mongoTemplate.upsert(query, update, TUser::class.java)
        if (result.matchedCount == 1L) {
            return true
        }
        return false
    }

    override fun removeToken(uId: String, token: String): Boolean {
        val user = userRepository.findOneByUId(uId)
        if (user == null) {
            logger.warn("user [$uId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        val query = Query.query(Criteria.where("uId").`is`(uId))
        val s = BasicDBObject()
        s["id"] = token
        val update = Update()
        update.pull("tokens", s)
        val result = mongoTemplate.updateFirst(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun getUserById(uId: String): User? {
        val user = userRepository.findOneByUId(uId) ?: return null
        return User(
            uId = user.uId!!,
            name = user.name,
            pwd = "",
            admin = user.admin,
            locked = user.locked,
            tokens = user.tokens,
            roles = user.roles
        )
    }

    override fun findUserByUserToken(uId: String, pwd: String): User? {
        val hashPwd = DataDigestUtils.md5FromStr(pwd)
        val criteria = Criteria()
        criteria.orOperator(Criteria.where("pwd").`is`(hashPwd), Criteria.where("tokens.id").`is`(pwd)).and("uId").`is`(uId)
        val query = Query.query(criteria)
        val result = mongoTemplate.findOne(query, TUser::class.java) ?: return  throw ErrorCodeException(AuthMessageCode.AUTH_USER_TOKEN_ERROR)
        return User(
            uId = result.uId!!,
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