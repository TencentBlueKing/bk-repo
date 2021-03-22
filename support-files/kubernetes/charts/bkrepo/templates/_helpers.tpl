{{/*
Return the proper Docker Image Registry Secret Names
*/}}
{{- define "bkrepo.imagePullSecrets" -}}
{{- include "common.images.pullSecrets" (dict "images" (list .Values.gateway.image .Values.repository.image .Values.auth.image .Values.init.mongodb.image .Values.generic.image .Values.docker.image .Values.npm.image .Values.pypi.image .Values.helm.image) "global" .Values.global) -}}
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
