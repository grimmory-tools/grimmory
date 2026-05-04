{{/*
Validation — database configuration preconditions.

Ensures that the bundled MariaDB subchart and external database
configuration are not both specified, and that at least one is set.
*/}}
{{- define "grimmory.validate-database" -}}
{{- if and .Values.mariadb.enabled .Values.externalDatabase.host }}
{{- fail "mariadb.enabled and externalDatabase are mutually exclusive: disable the bundled MariaDB subchart or remove the externalDatabase" }}
{{- end }}
{{- if and (not .Values.mariadb.enabled) (not .Values.externalDatabase.host) }}
{{- fail "At least one database must be set: enable mariadb or provide an externalDatabase" }}
{{- end }}
{{- end }}

{{/*
Connection helpers — resolve a single database configuration value.

Usage:  {{ include "grimmory.dbHost" . }}
*/}}

{{- define "grimmory.dbHost" -}}
{{- if .Values.mariadb.enabled -}}
{{- printf "%s-mariadb" .Release.Name -}}
{{- else -}}
{{- required "externalDatabase.host is required" .Values.externalDatabase.host -}}
{{- end -}}
{{- end -}}

{{- define "grimmory.dbPort" -}}
{{- if .Values.mariadb.enabled -}}
{{- .Values.mariadb.service.port -}}
{{- else -}}
{{- .Values.externalDatabase.port | default 3306 -}}
{{- end -}}
{{- end -}}

{{- define "grimmory.dbName" -}}
{{- if .Values.mariadb.enabled -}}
{{- .Values.mariadb.auth.database -}}
{{- else -}}
{{- required "externalDatabase.database is required" .Values.externalDatabase.database -}}
{{- end -}}
{{- end -}}

{{- define "grimmory.dbUserName" -}}
{{- if .Values.mariadb.enabled -}}
{{- .Values.mariadb.auth.username -}}
{{- else -}}
{{- required "externalDatabase.username is required" .Values.externalDatabase.username -}}
{{- end -}}
{{- end -}}

{{- define "grimmory.dbPasswordSecretName" -}}
{{- if .Values.mariadb.enabled -}}
{{- if .Values.mariadb.auth.existingSecret -}}
{{- .Values.mariadb.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-mariadb" .Release.Name -}}
{{- end -}}
{{- else -}}
{{- required "externalDatabase.existingSecret is required" .Values.externalDatabase.existingSecret -}}
{{- end -}}
{{- end -}}

{{- define "grimmory.dbPasswordSecretKey" -}}
{{- if .Values.mariadb.enabled -}}
{{- if .Values.mariadb.auth.existingSecret -}}
{{- required "mariadb.auth.secretKeys.userPasswordKey is required" .Values.mariadb.auth.secretKeys.userPasswordKey -}}
{{- else -}}
mariadb-password
{{- end -}}
{{- else -}}
{{- required "externalDatabase.existingSecretPasswordKey is required" .Values.externalDatabase.existingSecretPasswordKey -}}
{{- end -}}
{{- end -}}
