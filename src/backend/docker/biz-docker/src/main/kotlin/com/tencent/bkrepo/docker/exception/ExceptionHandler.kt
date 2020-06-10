package com.tencent.bkrepo.docker.exception

import com.netflix.client.ClientException
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.exception.ExternalErrorCodeException
import com.tencent.bkrepo.docker.errors.DockerV2Errors
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 统一异常处理
 */
@RestControllerAdvice
class ExceptionHandler {

    @ExceptionHandler(ExternalErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleExternalErrorCodeException(exception: ExternalErrorCodeException): ResponseEntity<Any> {
        logger.warn("failed with external error code exception:[${exception.errorCode}-${exception.errorMessage}]")
        return DockerV2Errors.internalError(exception.errorMessage)
    }

    @ExceptionHandler(ErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleErrorCodeException(exception: ErrorCodeException): ResponseEntity<Any> {
        logger.warn("failed with error code exception:[${exception.message}]")
        return DockerV2Errors.internalError(exception.message)
    }

    @ExceptionHandler(ClientException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleClientException(exception: ClientException): ResponseEntity<Any> {
        logger.error("failed with client exception:[$exception]", exception)
        return DockerV2Errors.internalError(exception.errorMessage)
    }

    @ExceptionHandler(HystrixRuntimeException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleHystrixRuntimeException(exception: HystrixRuntimeException): ResponseEntity<Any> {
        logger.error("failed with hystrix exception:[${exception.failureType}-${exception.message}]", exception)
        return DockerV2Errors.internalError(exception.message)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: Exception): ResponseEntity<Any> {
        if (exception.cause is ClientException) {
            logger.error("failed with client exception:[${exception.cause}]")
            return DockerV2Errors.internalError(exception.message)
        }
        logger.error("Failed with other exception:[${exception.message}]", exception)
        return DockerV2Errors.internalError(exception.message)
    }

    @ExceptionHandler(DockerRepoNotFoundException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleExternalDockerReporNotFoundExceptionn(exception: DockerRepoNotFoundException): ResponseEntity<Any> {
        logger.warn("failed with repo not found exception:[${exception.message}]")
        return DockerV2Errors.repoInvalid(exception.message!!)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExceptionHandler::class.java)
    }
}
