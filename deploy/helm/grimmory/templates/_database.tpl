{{/*
Validation — database configuration preconditions.

Ensures that the bundled MariaDB subchart and external database
configuration are not both specified, and that at least one is set.
*/}}
{{- define "grimmory.validate-database" -}}
	{{- if and .Values.mariadb.enabled .Values.externalDatabase.enabled }}
		{{- fail "mariadb.enabled and externalDatabase.enabled are mutually exclusive: disable the bundled MariaDB subchart or remove the externalDatabase" }}
	{{- end }}
	{{- if and (not .Values.mariadb.enabled) (not .Values.externalDatabase.enabled) }}
		{{- fail "At least one database must be set: enable mariadb or provide an externalDatabase" }}
	{{- end }}
	{{- if .Values.externalDatabase.enabled }}
		{{- with .Values.externalDatabase }}
			{{- if and (not .host) (or (not .existingSecret.enabled) (and .existingSecret.enabled (not .existingSecret.hostnameKey)))}}
				{{- fail "No host is set for the externalDatabase." }}
			{{- end }}
			{{- if and (not .password) (or (not .existingSecret.enabled) (and .existingSecret.enabled (not .existingSecret.passwordKey)))}}
				{{- fail "No password is set for the externalDatabase." }}
			{{- end }}
		{{- end }}
	{{- end }}
{{- end -}}

{{/*
Connection helpers — resolve a single database configuration value.

Usage:  {{ include "grimmory.dbHostname" . }}
*/}}
{{- define "grimmory.dbHostname" -}}
	{{- if .Values.externalDatabase.enabled }}
		{{- with .Values.externalDatabase }}
			{{- if and .existingSecret.enabled .existingSecret.hostnameKey }}
				{{- fail "Chart bug, this helper should not be called when an existing secret is set" }}
			{{- else }}
				{{- .host }}
			{{- end }}
		{{- end }}
	{{- else }}
		{{- printf "%s-mariadb" .Release.Name }}
	{{- end }}
{{- end -}}

{{- define "grimmory.dbPort" -}}
	{{- if .Values.externalDatabase.enabled }}
		{{- with .Values.externalDatabase }}
			{{- if and .existingSecret.enabled .existingSecret.portKey }}
				{{- fail "Chart bug, this helper should not be called when an existing secret is set" }}
			{{- else }}
				{{- .port }}
			{{- end }}
		{{- end }}
	{{- else }}
		{{- .Values.mariadb.service.port }}
	{{- end }}
{{- end -}}

{{- define "grimmory.dbName" -}}
	{{- if .Values.externalDatabase.enabled }}
		{{- with .Values.externalDatabase }}
			{{- if and .existingSecret.enabled .existingSecret.databaseKey }}
				{{- fail "Chart bug, this helper should not be called when an existing secret is set" }}
			{{- else }}
				{{- .database }}
			{{- end }}
		{{- end }}
	{{- else }}
		{{- .Values.mariadb.auth.database }}
	{{- end }}
{{- end -}}

{{- define "grimmory.dbUserName" -}}
	{{- if .Values.externalDatabase.enabled }}
		{{- with .Values.externalDatabase }}
			{{- if and .existingSecret.enabled .existingSecret.usernameKey }}
				{{- fail "Chart bug, this helper should not be called when an existing secret is set" }}
			{{- else }}
				{{- .username }}
			{{- end }}
		{{- end }}
	{{- else }}
		{{- .Values.mariadb.auth.username }}
	{{- end }}
{{- end -}}


{{- define "grimmory.dbPasswordSecretName" -}}
	{{- if .Values.externalDatabase.enabled }}
		{{- with .Values.externalDatabase }}
			{{- required "No secretName was set for the externalDatabase existingSecret." .existingSecret.secretName }}
		{{- end }}
	{{- else }}
		{{- with .Values.mariadb.auth }}
			{{- if .existingSecret }}
				{{- .existingSecret }}
			{{- else }}
				{{- printf "%s-mariadb" $.Release.Name }}
			{{- end }}
		{{- end }}
	{{- end }}
{{- end -}}

{{- define "grimmory.dbPasswordSecretKey" -}}
	{{- if .Values.externalDatabase.enabled }}
		{{- with .Values.externalDatabase }}
			{{- required "No passwordKey was set for the externalDatabase existingSecret." .existingSecret.passwordKey }}
		{{- end }}
	{{- else }}
		{{- with .Values.mariadb.auth }}
			{{- if not .existingSecret }}
				{{- "mariadb-password" }}
			{{- else }}
				{{- required "No userPasswordKey was set for the built-in database existingSecret" .secretKeys.userPasswordKey }}
			{{- end }}
		{{- end }}
	{{- end }}
{{- end -}}

