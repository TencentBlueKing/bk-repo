package com.tencent.bkrepo.git.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.thread.TransmitterExecutorWrapper
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.git.constant.GitMessageCode
import com.tencent.bkrepo.git.internal.CodeRepositoryResolver
import com.tencent.bkrepo.git.server.DefaultReceivePackFactory
import com.tencent.bkrepo.git.server.SmartOutputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN
import jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR
import jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
import jakarta.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE
import org.eclipse.jgit.errors.CorruptObjectException
import org.eclipse.jgit.errors.PackProtocolException
import org.eclipse.jgit.errors.UnpackException
import org.eclipse.jgit.http.server.GitSmartHttpTools
import org.eclipse.jgit.http.server.GitSmartHttpTools.UPLOAD_PACK_REQUEST_TYPE
import org.eclipse.jgit.http.server.GitSmartHttpTools.UPLOAD_PACK_RESULT_TYPE
import org.eclipse.jgit.http.server.GitSmartHttpTools.sendError
import org.eclipse.jgit.http.server.HttpServerText
import org.eclipse.jgit.http.server.ServletUtils
import org.eclipse.jgit.http.server.ServletUtils.consumeRequestBody
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.pack.PackConfig
import org.eclipse.jgit.transport.InternalHttpServerGlue
import org.eclipse.jgit.transport.PacketLineOut
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.RefAdvertiser
import org.eclipse.jgit.transport.ServiceMayNotContinueException
import org.eclipse.jgit.transport.UploadPack
import org.eclipse.jgit.transport.UploadPackInternalServerErrorException
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException
import org.eclipse.jgit.util.HttpSupport
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.text.MessageFormat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Git服务
 * */
@Service
class GitService {
    companion object {
        val uploadPackFactory: DefaultUploadPackFactory = DefaultUploadPackFactory()
        val receivePackFactory: DefaultReceivePackFactory = DefaultReceivePackFactory()
        val executor: ThreadPoolExecutor = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            200, 60, TimeUnit.SECONDS, LinkedBlockingQueue(10000),
            ThreadFactoryBuilder().setNameFormat("code-%d").build(),
        )
        val transmitterExecutor = TransmitterExecutorWrapper(executor)
        private val logger = LoggerFactory.getLogger(GitService::class.java)
    }

    fun infoRefs(svc: String) {
        with(ArtifactContext()) {
            val db = CodeRepositoryResolver.open(projectId, repoName, storageCredentials)
            val up = uploadPackFactory.create(request, db)
            doInfoRefs(up, request, response, svc)
        }
    }

    fun gitUploadPack() {
        with(ArtifactContext()) {
            val db = CodeRepositoryResolver.open(projectId, repoName, storageCredentials)
            val up = uploadPackFactory.create(request, db)
            val packConfig = PackConfig(db)
            packConfig.executor = transmitterExecutor
            up.setPackConfig(packConfig)
            doUpload(up, request, response)
        }
    }

    fun gitReceivePack() {
        val context = ArtifactContext()
        with(context) {
            val db = CodeRepositoryResolver.open(projectId, repoName, storageCredentials)
            val rp = receivePackFactory.create(request, db, context)
            doReceive(rp, request, response, this)
        }
    }

    private fun doUpload(
        up: UploadPack,
        req: HttpServletRequest,
        rsp: HttpServletResponse
    ) {
        if (UPLOAD_PACK_REQUEST_TYPE != req.contentType) {
            rsp.sendError(SC_UNSUPPORTED_MEDIA_TYPE)
            return
        }
        val out = SmartOutputStream(req, rsp, false)
        try {
            up.isBiDirectionalPipe = false
            rsp.contentType = UPLOAD_PACK_RESULT_TYPE
            out.use {
                up.uploadWithExceptionPropagation(
                    ServletUtils.getInputStream(req),
                    out,
                    null
                )
            }
        } catch (e: ServiceMayNotContinueException) {
            if (e.isOutput) {
                consumeRequestBody(req)
            }
            throw e
        } catch (e: UploadPackInternalServerErrorException) {
            logger.error(
                MessageFormat.format(
                    HttpServerText.get().internalErrorDuringUploadPack,
                    identify(up.repository)
                ),
                e
            )
            consumeRequestBody(req)
        } catch (e: ServiceMayNotContinueException) {
            if (!e.isOutput && !rsp.isCommitted) {
                rsp.reset()
                sendError(req, rsp, e.statusCode, e.message)
            }
        } catch (e: Throwable) {
            logger.error(
                MessageFormat.format(
                    HttpServerText.get().internalErrorDuringUploadPack,
                    identify(up.repository)
                ),
                e
            )
            if (!rsp.isCommitted) {
                rsp.reset()
                val msg = (e as? PackProtocolException)?.message
                sendError(req, rsp, SC_INTERNAL_SERVER_ERROR, msg)
            }
        }
    }

    private fun doInfoRefs(
        up: UploadPack,
        req: HttpServletRequest,
        res: HttpServletResponse,
        svc: String
    ) {
        val buf = SmartOutputStream(req, res, true)
        try {
            InternalHttpServerGlue.setPeerUserAgent(
                up,
                req.getHeader(HttpSupport.HDR_USER_AGENT)
            )
            res.contentType = infoRefsResultType(svc)
            up.isBiDirectionalPipe = false
            buf.use {
                val out = PacketLineOut(buf)
                up.sendAdvertisedRefs(RefAdvertiser.PacketLineOutRefAdvertiser(out), svc)
            }
        } catch (e: ServiceNotAuthorizedException) {
            res.sendError(SC_UNAUTHORIZED, e.message)
        } catch (e: ServiceNotEnabledException) {
            sendError(req, res, SC_FORBIDDEN, e.message)
        } catch (e: ServiceMayNotContinueException) {
            if (e.isOutput) buf.close() else sendError(req, res, e.statusCode, e.message)
        }
    }

    private fun doReceive(
        rp: ReceivePack,
        req: HttpServletRequest,
        rsp: HttpServletResponse,
        context: ArtifactContext
    ) {
        if (context.repositoryDetail.category == RepositoryCategory.REMOTE) {
            rsp.sendError(
                SC_FORBIDDEN,
                LocaleMessageUtils.getLocalizedMessage(
                    GitMessageCode.GIT_REMOTE_REPO_PUSH_NOT_SUPPORT
                )
            )
            logger.info("refuse git push request for remote repository ${context.repoName}")
            return
        }
        val out = SmartOutputStream(req, rsp, false)
        try {
            rp.isBiDirectionalPipe = false
            rsp.contentType = GitSmartHttpTools.RECEIVE_PACK_RESULT_TYPE
            out.use {
                rp.receive(ServletUtils.getInputStream(req), out, null)
            }
        } catch (e: ServiceNotAuthorizedException) {
            rsp.sendError(SC_UNAUTHORIZED, e.message)
            return
        } catch (e: ServiceNotEnabledException) {
            sendError(req, rsp, SC_FORBIDDEN, e.message)
            return
        } catch (e: CorruptObjectException) {
            logger.error(
                MessageFormat.format(
                    HttpServerText.get().receivedCorruptObject,
                    e.message,
                    identify(rp.repository)
                )
            )
            consumeRequestBody(req)
        } catch (e: Throwable) {
            logger.error(
                MessageFormat.format(
                    HttpServerText.get().internalErrorDuringReceivePack,
                    identify(rp.repository)
                ),
                e
            )
            when (e) {
                is UnpackException, is PackProtocolException -> {
                    consumeRequestBody(req)
                }
            }
            if (!rsp.isCommitted) {
                rsp.reset()
                sendError(req, rsp, SC_INTERNAL_SERVER_ERROR)
            }
        }
    }

    fun infoRefsResultType(svc: String): String? {
        return "application/x-$svc-advertisement"
    }

    fun identify(git: Repository): String? {
        return git.identifier ?: return "unknown"
    }
}
