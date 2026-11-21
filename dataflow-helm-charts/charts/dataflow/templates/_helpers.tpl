{{- define "dataflow.name" -}}
{{- .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "dataflow.labels" -}}
app.kubernetes.io/part-of: dataflow
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "dataflow.dataNamespace" -}}
{{- default (printf "%s-data" .Release.Namespace) .Values.namespaces.data -}}
{{- end -}}

{{- define "dataflow.jobsNamespace" -}}
{{- default (printf "%s-jobs" .Release.Namespace) .Values.namespaces.jobs -}}
{{- end -}}

{{- define "dataflow.clickhouseHost" -}}
{{- default (printf "%s-clickhouse.%s.svc" (include "dataflow.name" .) (include "dataflow.dataNamespace" .)) .Values.clickhouse.host -}}
{{- end -}}

{{ define "dataflow.endpoint" -}}
{{ if .Values.lake.endpoint -}}
{{- .Values.lake.endpoint -}}
{{- else if .Values.rustfs.enabled -}}
http://{{ include "dataflow.name" . }}-rustfs.{{ include "dataflow.dataNamespace" . }}.svc:9000
{{- end -}}
{{- end -}}

{{- define "dataflow.lakeUrl" -}}
{{- $root := urlParse .Values.lake.root -}}
{{- $prefix := trimAll "/" $root.path -}}
{{- if eq $root.scheme "gs" -}}
https://storage.googleapis.com/{{ $root.host }}/{{ if $prefix }}{{ $prefix }}/{{ end }}
{{- else if or (eq $root.scheme "s3a") (eq $root.scheme "s3") -}}
{{- $endpoint := include "dataflow.endpoint" . -}}
{{- if $endpoint -}}
{{ trimSuffix "/" $endpoint }}/{{ $root.host }}/{{ if $prefix }}{{ $prefix }}/{{ end }}
{{- else -}}
https://{{ $root.host }}.s3.{{ .Values.lake.region }}.amazonaws.com/{{ if $prefix }}{{ $prefix }}/{{ end }}
{{- end -}}
{{- else -}}
{{- fail "The ClickHouse lake URL requires s3a:// or gs://" -}}
{{- end -}}
{{- end -}}

{{- define "dataflow.authMode" -}}
{{- if .Values.keycloak.enabled -}}oidc{{- else -}}{{ .Values.auth.mode }}{{- end -}}
{{- end -}}

{{- define "dataflow.issuer" -}}
{{- if .Values.keycloak.enabled -}}
https://{{ required "keycloak.enabled needs gateway.hostname" .Values.gateway.hostname }}/auth/realms/dataflow
{{- else -}}
{{ .Values.auth.issuer }}
{{- end -}}
{{- end -}}

{{- define "dataflow.issuerInternal" -}}
{{- if .Values.keycloak.enabled -}}
http://{{ include "dataflow.name" . }}-keycloak:8080/auth/realms/dataflow
{{- else -}}
{{ default .Values.auth.issuer .Values.auth.internal }}
{{- end -}}
{{- end -}}

{{- define "dataflow.towerWebserver" -}}
{{- printf "%s-dagster-webserver" .Release.Name -}}
{{- end -}}
