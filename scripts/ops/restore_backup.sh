#!/usr/bin/env bash
set -euo pipefail

ORGANIZER_ENV_FILE="${ORGANIZER_ENV_FILE:-/etc/organizer/organizer.env}"
if [[ -r "$ORGANIZER_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ORGANIZER_ENV_FILE"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
timestamp() { date +'%Y-%m-%d %H:%M:%S'; }
log() { echo "[$(timestamp)] $*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "Comando '$1' nao encontrado."; exit 1; }; }

USER_HOME="${USER_HOME:-$HOME}"
BASE_DIR="${BASE_DIR:-${DOCUMENTS_DIR:-$USER_HOME}}"
BACKUP_DIR="${BACKUP_DIR:-$BASE_DIR/backup_database}"
ORGANIZER_DIR="${ORGANIZER_DIR:-$BASE_DIR/organizador-producao}"
COMPOSE_FILE="${COMPOSE_FILE:-$ORGANIZER_DIR/docker-compose.yml}"

POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-teste01}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-1234}"

MINIO_HOST="${MINIO_HOST:-127.0.0.1}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://${MINIO_HOST}:9000}"
MINIO_BUCKET="${MINIO_BUCKET:-facas-renders}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minio}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minio123}"
MINIO_DOCKER_NETWORK="${MINIO_DOCKER_NETWORK:-organizador-producao_organizador-producao-mynetwork}"
MINIO_MC_IMAGE="${MINIO_MC_IMAGE:-minio/mc:RELEASE.2024-10-29T15-34-59Z}"
MINIO_RESTORE_DIR="${MINIO_RESTORE_DIR:-$BACKUP_DIR/minio_restore}"

POSTGRES_FILE=""
MINIO_FILE=""
CHOOSE=false
RESTORE_POSTGRES=true
RESTORE_MINIO=true

usage() {
  cat <<'EOF'
Uso: ./restore_backup.sh [opcoes]

  --choose, -c             Seleciona os arquivos interativamente.
  --postgres-file <path>   Usa dump especifico de Postgres.
  --minio-file <path>      Usa backup especifico de MinIO.
  --postgres-only          Restaura apenas o Postgres.
  --minio-only             Restaura apenas o MinIO.
  --help                   Mostra esta ajuda.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --choose|-c) CHOOSE=true ;;
    --postgres-file) shift; POSTGRES_FILE="${1:-}" ;;
    --minio-file) shift; MINIO_FILE="${1:-}" ;;
    --postgres-only) RESTORE_MINIO=false ;;
    --minio-only) RESTORE_POSTGRES=false ;;
    --help) usage; exit 0 ;;
    *) echo "Opcao desconhecida: $1" >&2; usage; exit 1 ;;
  esac
  shift
done

if [[ "$RESTORE_POSTGRES" == false && "$RESTORE_MINIO" == false ]]; then
  echo "Nada para restaurar." >&2
  exit 1
fi

need docker
need tar
need ls

if [[ ! -d "$ORGANIZER_DIR" || ! -f "$COMPOSE_FILE" ]]; then
  echo "Projeto Organizer nao encontrado em '$ORGANIZER_DIR'." >&2
  exit 1
fi

compose() {
  docker compose -f "$COMPOSE_FILE" --project-directory "$ORGANIZER_DIR" "$@"
}

select_backup_file() {
  local label="$1"
  local glob="$2"
  local provided="$3"
  local -a files=()

  if [[ -n "$provided" ]]; then
    if [[ ! -f "$provided" ]]; then
      echo "Arquivo nao encontrado: $provided" >&2
      exit 1
    fi
    echo "$provided"
    return
  fi

  mapfile -t files < <(ls -1t $glob 2>/dev/null || true)
  if [[ ${#files[@]} -eq 0 ]]; then
    echo "Nenhum arquivo encontrado para '$label' ($glob)." >&2
    exit 1
  fi

  if [[ "$CHOOSE" == true ]]; then
    echo "Selecione $label:"
    local idx=1
    for f in "${files[@]}"; do
      printf "  [%d] %s\n" "$idx" "$f"
      ((idx++))
    done
    while true; do
      read -rp "Numero: " choice
      if [[ "$choice" =~ ^[0-9]+$ && "$choice" -ge 1 && "$choice" -le "${#files[@]}" ]]; then
        echo "${files[choice-1]}"
        return
      fi
      echo "Opcao invalida."
    done
  fi

  echo "${files[0]}"
}

log "Iniciando restore com BACKUP_DIR=$BACKUP_DIR"

if [[ "$RESTORE_POSTGRES" == true ]]; then
  POSTGRES_FILE="$(select_backup_file "backup do Postgres" "$BACKUP_DIR/backup_*.dump.gz" "$POSTGRES_FILE")"
  log "Dump Postgres selecionado: $POSTGRES_FILE"
fi
if [[ "$RESTORE_MINIO" == true ]]; then
  MINIO_FILE="$(select_backup_file "backup do MinIO" "$BACKUP_DIR/backup_minio_*.tar.gz" "$MINIO_FILE")"
  log "Backup MinIO selecionado: $MINIO_FILE"
fi

log "Subindo servicos base (postgres + minio)..."
compose up -d postgres-container minio minio-init

if [[ "$RESTORE_POSTGRES" == true ]]; then
  log "Restaurando Postgres (db=$POSTGRES_DB)..."
  gunzip -c "$POSTGRES_FILE" | docker exec -i postgres-container \
    env PGPASSWORD="$POSTGRES_PASSWORD" \
    pg_restore --clean --if-exists --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB"
  log "Postgres restaurado."
fi

if [[ "$RESTORE_MINIO" == true ]]; then
  log "Preparando restore MinIO..."
  rm -rf "$MINIO_RESTORE_DIR"
  mkdir -p "$MINIO_RESTORE_DIR"
  tar -xzf "$MINIO_FILE" -C "$MINIO_RESTORE_DIR"

  MC_RESTORE_CMD="set -euo pipefail
export MC_CONFIG_DIR=/tmp/mc-config
mkdir -p \$MC_CONFIG_DIR
mc alias set localminio '${MINIO_ENDPOINT}' '${MINIO_ACCESS_KEY}' '${MINIO_SECRET_KEY}' >/dev/null
mc mirror --overwrite --remove /restore/${MINIO_BUCKET} localminio/${MINIO_BUCKET}"

  run_mc_restore() {
    local -a network_args=()
    if [[ -n "$MINIO_DOCKER_NETWORK" ]]; then
      network_args+=(--network "$MINIO_DOCKER_NETWORK")
    fi
    docker run --rm \
      "${network_args[@]}" \
      -v "$MINIO_RESTORE_DIR:/restore" \
      --entrypoint /bin/sh \
      "$MINIO_MC_IMAGE" \
      -c "$MC_RESTORE_CMD"
  }

  if ! run_mc_restore; then
    if [[ -n "$MINIO_DOCKER_NETWORK" ]]; then
      log "Falha com rede '$MINIO_DOCKER_NETWORK'. Tentando sem --network..."
      MINIO_DOCKER_NETWORK=""
      run_mc_restore
    else
      log "ERRO: falha ao restaurar MinIO."
      exit 2
    fi
  fi
  log "MinIO restaurado."
fi

log "Restore concluido com sucesso."
