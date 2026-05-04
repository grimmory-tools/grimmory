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
