set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

mod api 'backend/Justfile'
mod ui 'frontend/Justfile'
mod release 'tools/release/Justfile'

compose_file := env_var_or_default('GRIMMORY_COMPOSE_FILE', 'dev.docker-compose.yml')
compose_cmd := 'docker compose -f ' + compose_file
db_service := 'backend_db'
local_image_tag := env_var_or_default('GRIMMORY_IMAGE_TAG', 'grimmory:local')
local_container_name := 'grimmory-local'
local_db_url := 'jdbc:mariadb://localhost:3366/grimmory?createDatabaseIfNotExist=true'
local_db_user := 'grimmory'
local_db_password := 'grimmory'

# Show the primary developer and agent command surface, including submodule recipes.
help:
    @just --list --list-submodules

# List recipes in a specific module, for example `just list api` or `just list ui`.
list module='':
    @if [[ -n "{{ module }}" ]]; then \
      just --list "{{ module }}"; \
    else \
      just --list --list-submodules; \
    fi

# Install the common local prerequisites used by the UI and API workflows.
bootstrap: ui::install api::version

# Run the full local verification pass used before opening a PR.
check: api::check ui::check

# Run the frontend and backend test suites.
test: api::test ui::test

# Build both application components without publishing a container image.
build: api::build ui::build

# Start the Docker-based development stack in the foreground.
dev-up:
    {{ compose_cmd }} up

# Start the Docker-based development stack in the background.
dev-up-detached:
    {{ compose_cmd }} up -d

# Start only the development database service from the compose stack.
db-up:
    {{ compose_cmd }} up -d {{ db_service }}

# Stop only the development database service from the compose stack.
db-down:
    {{ compose_cmd }} stop {{ db_service }}

# Stop the Docker-based development stack.
dev-down:
    {{ compose_cmd }} down

# Tail logs from the full dev stack or a single service with `just dev-logs backend`.
dev-logs service='':
    @if [[ -n "{{ service }}" ]]; then \
      {{ compose_cmd }} logs -f "{{ service }}"; \
    else \
      {{ compose_cmd }} logs -f; \
    fi

# Build the production image locally with buildx. Usage: `just image-build [platform] [tag]`.
image-build platform='linux/amd64' tag=local_image_tag:
    docker buildx build --platform "{{ platform }}" -t "{{ tag }}" --load .

# Run the locally built production image against the expected development defaults.
image-run tag=local_image_tag db_url=local_db_url db_user=local_db_user db_password=local_db_password:
    docker run --rm -it \
      --name "{{ local_container_name }}" \
      --network host \
      -e "SPRING_DATASOURCE_URL={{ db_url }}" \
      -e "SPRING_DATASOURCE_USERNAME={{ db_user }}" \
      -e "SPRING_DATASOURCE_PASSWORD={{ db_password }}" \
      -v ./shared/data:/app/data \
      -v ./shared/books:/books \
      -v ./shared/bookdrop:/bookdrop \
      -p 6060:6060 \
      "{{ tag }}"

# Show the resolved tool versions that the local commands expect to find.
doctor:
    @echo "just: $$(just --version)"
    @echo "java: $$(java -version 2>&1 | head -n 1)"
    @echo "node: $$(node --version)"
    @echo "yarn: $$(corepack yarn --version)"
    @echo "docker: $$(docker --version)"
