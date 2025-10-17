package com.tencent.bkrepo.media.job.service

import com.tencent.bkrepo.media.job.k8s.K8sProperties
import com.tencent.bkrepo.media.job.service.TranscodeJobService.Companion.TRANSCODE_JOB_APP_LABEL_KEY
import com.tencent.bkrepo.media.job.service.TranscodeJobService.Companion.TRANSCODE_JOB_APP_LABEL_VALUE
import io.kubernetes.client.informer.SharedInformerFactory
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobList
import io.kubernetes.client.util.CallGeneratorParams
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Duration
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy


@Component
class TranscodeJobInformerController @Autowired constructor(
    private val informerFactory: SharedInformerFactory,
    private val transcodeJobEventHandler: TranscodeJobEventHandler,
    private val informerClient: ApiClient,
    private val k8sProperties: K8sProperties,
) {
    @PostConstruct
    fun start() {
        logger.info("Initializing Job Informer for namespace: {}", k8sProperties.namespace)
        val jobInformer = informerFactory.sharedIndexInformerFor(
            { params: CallGeneratorParams ->
                BatchV1Api(informerClient).listNamespacedJobCall(
                    /* namespace = */ k8sProperties.namespace,
                    /* pretty = */ null,
                    /* allowWatchBookmarks = */ null,
                    /* _continue = */ null,
                    /* fieldSelector = */ null,
                    /* labelSelector = */ "$TRANSCODE_JOB_APP_LABEL_KEY=$TRANSCODE_JOB_APP_LABEL_VALUE",
                    /* limit = */ null,
                    /* resourceVersion = */ params.resourceVersion,
                    /* resourceVersionMatch = */ null,
                    /* timeoutSeconds = */ params.timeoutSeconds,
                    /* watch = */ params.watch,
                    /* _callback = */ null
                )
            },
            V1Job::class.java,
            V1JobList::class.java,
            Duration.ofMinutes(1).toMillis()
        )
        jobInformer.addEventHandler(transcodeJobEventHandler)
        logger.info("Starting Informer factory...")
        informerFactory.startAllRegisteredInformers()
        logger.info("Informer factory started successfully.")
    }

    @PreDestroy
    fun stop() {
        logger.info("Shutting down Informer factory...")
        informerFactory.stopAllRegisteredInformers()
        logger.info("Informer factory shut down.")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(TranscodeJobInformerController::class.java)
    }
}