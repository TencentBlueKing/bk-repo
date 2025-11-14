{{/*
Return the proper Docker Image Registry Secret Names
*/}}
{{- define "bkrepo.imagePullSecrets" -}}
{{- include "common.images.pullSecrets" (dict "images" (list
    .Values.gateway.image
    .Values.repository.image
    .Values.auth.image
    .Values.init.curl.image
    .Values.init.mongodb.image
    .Values.init.iam.image
    .Values.generic.image
    .Values.docker.image
    .Values.npm.image
    .Values.pypi.image
    .Values.helm.image
    .Values.job.image
    .Values.maven.image
    .Values.opdata.image
    .Values.preview.image
    .Values.replication.image
    .Values.s3.image
) "global" .Values.global) -}}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "bkrepo.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (printf "%s-foo" (include "common.names.fullname" .)) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
define init project id
*/}}
{{- define "bkrepo.init.project" -}}
{{- if .Values.gateway.enableMultiTenantMode -}}
{{- $tenantId := default "system" .Values.gateway.oPTenantId -}}
{{- printf "%s.blueking" $tenantId -}}
{{- else -}}
{{- printf "blueking" -}}
{{- end -}}
{{- end -}}



{{/*
Create a default fully qualified mongodb subchart.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "bkrepo.mongodb.fullname" -}}
{{- if .Values.mongodb.fullnameOverride -}}
{{- .Values.mongodb.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default "mongodb" .Values.mongodb.nameOverride -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/*
Return the mongodb connection uri
*/}}
{{- define "bkrepo.mongodbUri" -}}
{{- if eq .Values.mongodb.enabled true -}}
{{- printf "mongodb://%s:%s@%s:27017/%s" .Values.mongodb.auth.username .Values.mongodb.auth.password (include "bkrepo.mongodb.fullname" .) .Values.mongodb.auth.database -}}
{{- else -}}
{{- .Values.externalMongodb.uri -}}
{{- end -}}
{{- end -}}

{{/*
Return the label key of bk-repo scope
*/}}
{{- define "bkrepo.labelValues.scope" -}}
    {{- printf "bk.repo.scope" -}}
{{- end -}}

{{/*
Return the label value of bk-repo scope backend
*/}}
{{- define "bkrepo.labelValues.scope.backend" -}}
    {{- printf "backend" -}}
{{- end -}}

{{/*
Return the label value of bk-repo scope gateway
*/}}
{{- define "bkrepo.labelValues.scope.gateway" -}}
    {{- printf "gateway" -}}
{{- end -}}

{{/*
Return the proper image name
{{ include "bkrepo.images.image" ( dict "imageRoot" .Values.path.to.the.image "global" $) }}
*/}}
{{- define "bkrepo.images.image" -}}
{{- $registryName := .imageRoot.registry -}}
{{- $repositoryName := .imageRoot.repository -}}
{{- $tag := .imageRoot.tag | toString -}}
{{- if .global }}
    {{- if .global.imageRegistry }}
     {{- $registryName = .global.imageRegistry -}}
    {{- end -}}
{{- end -}}
{{- if .bkrepo.imageRegistry }}
    {{- $registryName = .bkrepo.imageRegistry -}}
{{- end -}}
{{- if .bkrepo.imageTag }}
    {{- $tag = .bkrepo.imageTag -}}
{{- end -}}
{{- if $registryName }}
{{- printf "%s/%s:%s" $registryName $repositoryName $tag -}}
{{- else -}}
{{- printf "%s:%s" $repositoryName $tag -}}
{{- end -}}
{{- end -}}
{{/*
Return the value of auth url
*/}}
{{- define "bkrepo.oci.authUrl" -}}
{{- if and .Values.gateway.service.nodeIP (and .Values.gateway.service.dockerNodePort (or (eq .Values.gateway.service.type "NodePort") (eq .Values.gateway.service.type "LoadBalancer"))) -}}
    {{- printf "%s:%s/v2/auth" .Values.gateway.service.nodeIP (.Values.gateway.service.dockerNodePort | toString) -}}
{{- else -}}
    {{- printf "%s/docker/v2/auth" .Values.gateway.host -}}
{{- end -}}
{{- end -}}

{{/*
Return the value of authorization
*/}}
{{- define "bkrepo.authorization" -}}
{{- if and .Values.gateway.accessKey .Values.gateway.secretKey -}}
    {{- $base64 := (printf "%s:%s" .Values.gateway.accessKey .Values.gateway.secretKey ) | b64enc -}}
    {{- printf "Platform %s" $base64 -}}
{{- else -}}
    {{- .Values.gateway.authorization -}}
{{- end -}}
{{- end -}}