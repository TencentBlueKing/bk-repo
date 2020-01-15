package com.tencent.bkrepo.npm.jwt

import com.tencent.bkrepo.npm.exception.NpmTokenIllegalException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.apache.commons.codec.binary.Base64
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object JwtUtils {
    /* 秘钥 */
    private const val SECRET = "bk_repo"

    /** token 过期时间 */
    private const val calendarField = Calendar.SECOND

    private const val jwtId = "tokenId"

    /**
     * 创建JWT
     */
    private fun createJWT(claims: Map<String, Any>, expireTime: Int): String {
        // 指定签名的时候使用的签名算法，也就是header那部分，jjwt已经将这部分内容封装好了。
        val signatureAlgorithm = SignatureAlgorithm.HS512
        val now = Date()
        // 生成JWT的时间
        val nowTime = Calendar.getInstance()
        val builder =
            Jwts.builder().setClaims(claims).setId(jwtId).setIssuedAt(now).signWith(
                signatureAlgorithm,
                generalKey()
            )
        if (expireTime > 0) {
            nowTime.add(
                calendarField,
                expireTime
            )
            builder.setExpiration(nowTime.time)
        }
        return builder.compact()
    }

    /**
     * 由字符串生成加密key
     */
    private fun generalKey(): SecretKey {
        val encodeKey = Base64.decodeBase64(SECRET)
        return SecretKeySpec(encodeKey, 0, encodeKey.size, "AES")
    }

    /**
     * 验证jwt
     */
    private fun verifyJwt(token: String): Claims {
        // 签名秘钥，和生成的签名的秘钥一模一样
        val key = generalKey()
        return try {
            Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(token).body
        } catch (expire: ExpiredJwtException) {
            throw NpmTokenIllegalException("token is expired")
        } catch (e: Exception) {
            // token 校验失败, 抛出Token验证非法异常
            throw NpmTokenIllegalException("bad props auth token")
        }
    }

    /**
     * 获取用户名
     *
     * @param token 令牌
     * @return 用户名
     */
    fun getUserName(token: String): String {
        val claims = verifyJwt(token)
        return claims["username", String::class.java]
    }

    /**
     * 根据userId生成token
     */
    fun generateToken(username: String, jwtProperties: JwtProperties): String {
        val map = HashMap<String, Any>()
        map["username"] = username
        return createJWT(map, jwtProperties.expireSecond)
    }
}
