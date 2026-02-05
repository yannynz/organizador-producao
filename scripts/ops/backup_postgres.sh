#!/usr/bin/env bash
set -euo pipefail

ORGANIZER_ENV_FILE="${ORGANIZER_ENV_FILE:-/etc/organizer/organizer.env}"
if [[ -r "$ORGANIZER_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ORGANIZER_ENV_FILE"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
timestamp() { date +'%Y-%m-%d %H:%M:%S'; }
is_true() { [[ "${1,,}" =~ ^(1|true|yes|y|on)$ ]]; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "[$(timestamp)] ERRO: comando '$1' nao encontrado."; exit 1; }; }

USER_HOME="${USER_HOME:-$HOME}"
BASE_DIR="${BASE_DIR:-${DOCUMENTS_DIR:-$USER_HOME}}"
BACKUP_DIR="${BACKUP_DIR:-$BASE_DIR/backup_database}"
MAX_BACKUPS="${MAX_BACKUPS:-7}"

POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-postgres-container}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-teste01}"

MINIO_HOST="${MINIO_HOST:-127.0.0.1}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://${MINIO_HOST}:9000}"
MINIO_SERVICE_ENDPOINT="${MINIO_SERVICE_ENDPOINT:-http://minio:9000}"
MINIO_BUCKET="${MINIO_BUCKET:-facas-renders}"
MINIO_DOCKER_NETWORK="${MINIO_DOCKER_NETWORK:-organizador-producao_organizador-producao-mynetwork}"
MINIO_MC_IMAGE="${MINIO_MC_IMAGE:-quay.io/minio/mc:RELEASE.2024-10-29T15-34-59Z-cpuv1}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minio}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minio123}"

REMOTE_BACKUP_ENABLED="${REMOTE_BACKUP_ENABLED:-false}"
REMOTE_HOST="${REMOTE_HOST:-}"
REMOTE_USER="${REMOTE_USER:-}"
REMOTE_DIR="${REMOTE_DIR:-}"
SSH_KEY="${SSH_KEY:-}"

mkdir -p "$BACKUP_DIR"
LOG_FILE="$BACKUP_DIR/backuplog.txt"
exec > >(tee -a "$LOG_FILE") 2>&1
umask 077

echo "[$(timestamp)] === Inicio do backup (Postgres + MinIO) ==="

need docker
need gzip
need rsync
need tar
need ssh

docker ps >/dev/null
docker exec "$POSTGRES_CONTAINER_NAME" pg_dump --version >/dev/null

if [[ -z "$MINIO_ACCESS_KEY" || -z "$MINIO_SECRET_KEY" ]]; then
  echo "[$(timestamp)] ERRO: credenciais do MinIO ausentes."
  exit 2
fi

TIMESTAMP="$(date +'%Y-%m-%d_%H-%M-%S')"
DB_BACKUP_FILE="$BACKUP_DIR/backup_${TIMESTAMP}.dump.gz"
MINIO_MIRROR_DIR="$BACKUP_DIR/minio_mirror"
MINIO_BACKUP_FILE="$BACKUP_DIR/backup_minio_${TIMESTAMP}.tar.gz"

mkdir -p "$MINIO_MIRROR_DIR"

echo "[$(timestamp)] Gerando dump Postgres..."
docker exec "$POSTGRES_CONTAINER_NAME" \
  pg_dump -U "$POSTGRES_USER" -F c "$POSTGRES_DB" | gzip > "$DB_BACKUP_FILE"
echo "[$(timestamp)] Dump salvo em: $DB_BACKUP_FILE"

echo "[$(timestamp)] Espelhando bucket MinIO: $MINIO_BUCKET"
run_mc() {
  local endpoint="$MINIO_ENDPOINT"
  local -a network_args=()
  if [[ -n "$MINIO_DOCKER_NETWORK" ]]; then
    network_args+=(--network "$MINIO_DOCKER_NETWORK")
    if [[ "$endpoint" == "http://127.0.0.1:9000" || "$endpoint" == "http://localhost:9000" ]]; then
      endpoint="$MINIO_SERVICE_ENDPOINT"
    fi
  fi
  local mc_cmd="set -euo pipefail
export MC_CONFIG_DIR=/tmp/mc-config
mkdir -p \$MC_CONFIG_DIR
mc alias set localminio '${endpoint}' '${MINIO_ACCESS_KEY}' '${MINIO_SECRET_KEY}' >/dev/null
mc mirror --overwrite --remove localminio/${MINIO_BUCKET} /mirror/${MINIO_BUCKET}"
  docker run --rm \
    "${network_args[@]}" \
    -v "$MINIO_MIRROR_DIR:/mirror" \
    --user "$(id -u):$(id -g)" \
    --entrypoint /bin/sh \
    "$MINIO_MC_IMAGE" \
    -c "$mc_cmd"
}

if ! run_mc; then
  if [[ -n "$MINIO_DOCKER_NETWORK" ]]; then
    echo "[$(timestamp)] Aviso: falha com rede '$MINIO_DOCKER_NETWORK'. Tentando sem --network..."
    MINIO_DOCKER_NETWORK=""
    run_mc
  else
    echo "[$(timestamp)] ERRO: nao foi possivel acessar o MinIO em '$MINIO_ENDPOINT'."
    exit 3
  fi
fi

echo "[$(timestamp)] Compactando espelho MinIO..."
tar -czf "$MINIO_BACKUP_FILE" -C "$MINIO_MIRROR_DIR" "$MINIO_BUCKET"
rm -rf "$MINIO_MIRROR_DIR/$MINIO_BUCKET"
echo "[$(timestamp)] Backup MinIO salvo em: $MINIO_BACKUP_FILE"

echo "[$(timestamp)] Aplicando retencao local (MAX_BACKUPS=$MAX_BACKUPS)..."
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'backup_*.dump.gz' \
  -printf '%T@ %p\n' | sort -nr | awk "NR>$MAX_BACKUPS {print \$2}" | xargs -r rm --
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'backup_minio_*.tar.gz' \
  -printf '%T@ %p\n' | sort -nr | awk "NR>$MAX_BACKUPS {print \$2}" | xargs -r rm --

if is_true "$REMOTE_BACKUP_ENABLED"; then
  if [[ -z "$REMOTE_HOST" || -z "$REMOTE_USER" || -z "$REMOTE_DIR" ]]; then
    echo "[$(timestamp)] ERRO: REMOTE_BACKUP_ENABLED=true, mas REMOTE_HOST/REMOTE_USER/REMOTE_DIR nao foram definidos."
    exit 4
  fi

  REMOTE_DB_DIR="$REMOTE_DIR"
  REMOTE_MINIO_DIR="$REMOTE_DIR/minio"
  REMOTE_IS_LOCAL=false
  if [[ "$REMOTE_HOST" == "localhost" || "$REMOTE_HOST" == "127.0.0.1" ]]; then
    REMOTE_IS_LOCAL=true
  fi

  echo "[$(timestamp)] Enviando backup para remoto: ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}"
  if [[ "$REMOTE_IS_LOCAL" == true ]]; then
    mkdir -p "$REMOTE_DB_DIR" "$REMOTE_MINIO_DIR"
    rsync -az --partial --inplace "$DB_BACKUP_FILE" "$REMOTE_DB_DIR/"
    rsync -az --partial --inplace "$MINIO_BACKUP_FILE" "$REMOTE_MINIO_DIR/"
    (cd "$REMOTE_DB_DIR" && { ls -1t backup_*.dump.gz 2>/dev/null || true; } | sed -e "1,${MAX_BACKUPS}d" | xargs -r rm --)
    (cd "$REMOTE_MINIO_DIR" && { ls -1t backup_minio_*.tar.gz 2>/dev/null || true; } | sed -e "1,${MAX_BACKUPS}d" | xargs -r rm --)
  else
    SSH_CMD=(ssh -o BatchMode=yes -o ConnectTimeout=8)
    RSYNC_SSH="ssh -o BatchMode=yes -o ConnectTimeout=8"
    if [[ -n "$SSH_KEY" ]]; then
      SSH_CMD+=(-i "$SSH_KEY")
      RSYNC_SSH+=" -i $SSH_KEY"
    fi

    "${SSH_CMD[@]}" "${REMOTE_USER}@${REMOTE_HOST}" "mkdir -p '$REMOTE_DB_DIR' '$REMOTE_MINIO_DIR'"
    rsync -az --partial --inplace -e "$RSYNC_SSH" "$DB_BACKUP_FILE" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DB_DIR}/"
    rsync -az --partial --inplace -e "$RSYNC_SSH" "$MINIO_BACKUP_FILE" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_MINIO_DIR}/"

    "${SSH_CMD[@]}" "${REMOTE_USER}@${REMOTE_HOST}" \
      "cd '$REMOTE_DB_DIR' && { ls -1t backup_*.dump.gz 2>/dev/null || true; } | sed -e '1,${MAX_BACKUPS}d' | xargs -r rm --"
    "${SSH_CMD[@]}" "${REMOTE_USER}@${REMOTE_HOST}" \
      "cd '$REMOTE_MINIO_DIR' && { ls -1t backup_minio_*.tar.gz 2>/dev/null || true; } | sed -e '1,${MAX_BACKUPS}d' | xargs -r rm --"
  fi

  echo "[$(timestamp)] Backup remoto concluido."
else
  echo "[$(timestamp)] Backup remoto desabilitado (REMOTE_BACKUP_ENABLED=false)."
fi

echo "[$(timestamp)] === Backup concluido com sucesso ==="
