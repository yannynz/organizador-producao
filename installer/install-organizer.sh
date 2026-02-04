#!/usr/bin/env bash
set -euo pipefail

MODE="install"
NON_INTERACTIVE=false
TARGET_USER="${TARGET_USER:-}"
SOURCE_REPO_DIR=""

ENV_FILE="/etc/organizer/organizer.env"
SYSTEMD_DIR="/etc/systemd/system"

fail() {
  echo "ERRO: $*" >&2
  exit 1
}

log() {
  printf '[%(%F %T)T] %s\n' -1 "$*"
}

prompt_msg() {
  printf '%s\n' "$*" >&2
}

need() {
  command -v "$1" >/dev/null 2>&1 || fail "comando '$1' nao encontrado."
}

is_true() {
  [[ "${1,,}" =~ ^(1|true|yes|y|on)$ ]]
}

usage() {
  cat <<'EOF'
Uso:
  sudo ./installer/install-organizer.sh [opcoes]

Modos:
  (padrao)               Instalacao/configuracao completa.
  --reconfigure-schedules
                          Reconfigura startup/timers sem reinstalar tudo.

Opcoes:
  --user <usuario>       Usuario dono da instalacao (default: SUDO_USER).
  --repo-dir <path>      Caminho do repositorio fonte (default: diretorio atual do repo).
  --non-interactive      Usa defaults e nao faz perguntas.
  --help                 Mostra esta ajuda.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reconfigure-schedules) MODE="reconfigure" ;;
    --user) shift; TARGET_USER="${1:-}" ;;
    --repo-dir) shift; SOURCE_REPO_DIR="${1:-}" ;;
    --non-interactive) NON_INTERACTIVE=true ;;
    --help|-h) usage; exit 0 ;;
    *) fail "opcao desconhecida: $1" ;;
  esac
  shift
done

if [[ $EUID -ne 0 ]]; then
  fail "execute com sudo/root."
fi

need docker
need rsync
need ip
need systemctl
docker compose version >/dev/null 2>&1 || fail "docker compose plugin nao encontrado."

if [[ -z "$TARGET_USER" ]]; then
  if [[ -n "${SUDO_USER:-}" && "${SUDO_USER:-}" != "root" ]]; then
    TARGET_USER="$SUDO_USER"
  else
    TARGET_USER="$(logname 2>/dev/null || true)"
  fi
fi
[[ -n "$TARGET_USER" ]] || fail "nao foi possivel detectar o usuario alvo. Use --user."
id "$TARGET_USER" >/dev/null 2>&1 || fail "usuario '$TARGET_USER' nao existe."

TARGET_HOME="$(getent passwd "$TARGET_USER" | cut -d: -f6)"
[[ -n "$TARGET_HOME" ]] || fail "nao foi possivel obter HOME do usuario '$TARGET_USER'."
BASE_DIR="${BASE_DIR:-${DOCUMENTS_DIR:-$TARGET_HOME}}"
TARGET_REPO_DIR="${ORGANIZER_DIR:-$BASE_DIR/organizador-producao}"
TARGET_BACKUP_DIR="${BACKUP_DIR:-$BASE_DIR/backup_database}"

if [[ -z "$SOURCE_REPO_DIR" ]]; then
  SOURCE_REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi
SOURCE_REPO_DIR="$(realpath "$SOURCE_REPO_DIR")"
[[ -f "$SOURCE_REPO_DIR/docker-compose.yml" ]] || fail "repositorio fonte invalido: '$SOURCE_REPO_DIR'."
[[ -f "$SOURCE_REPO_DIR/update-organizer" ]] || fail "arquivo update-organizer nao encontrado em '$SOURCE_REPO_DIR'."
[[ -f "$SOURCE_REPO_DIR/scripts/ops/backup_postgres.sh" ]] || fail "scripts/ops/backup_postgres.sh nao encontrado."
[[ -f "$SOURCE_REPO_DIR/scripts/ops/restore_backup.sh" ]] || fail "scripts/ops/restore_backup.sh nao encontrado."

HOSTNAME_VALUE="${HOSTNAME_VALUE:-$(hostnamectl --static 2>/dev/null || hostname)}"
SERVER_HOST="${SERVER_HOST:-}"
MINIO_HOST="${MINIO_HOST:-}"
APP_DXF_ANALYSIS_IMAGE_BASE_URL="${APP_DXF_ANALYSIS_IMAGE_BASE_URL:-}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-}"
MINIO_BUCKET="${MINIO_BUCKET:-facas-renders}"
MINIO_DOCKER_NETWORK="${MINIO_DOCKER_NETWORK:-organizador-producao_organizador-producao-mynetwork}"

POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-postgres-container}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-teste01}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-1234}"

MAX_BACKUPS="${MAX_BACKUPS:-2}"
REMOTE_BACKUP_ENABLED="${REMOTE_BACKUP_ENABLED:-true}"
REMOTE_HOST="${REMOTE_HOST:-192.168.10.31}"
REMOTE_USER="${REMOTE_USER:-monitor}"
REMOTE_DIR="${REMOTE_DIR:-/home/${REMOTE_USER}/backups_postgres}"
SSH_KEY="${SSH_KEY:-}"

STARTUP_ENABLED="${STARTUP_ENABLED:-true}"
BACKUP_TIMER_SPEC="${BACKUP_TIMER_SPEC:-calendar|*-*-* 02:00:00}"
RESTART_TIMER_SPEC="${RESTART_TIMER_SPEC:-disabled}"

if [[ -r "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

# Preserve existing paths on reconfiguration; full install keeps defaults above.
if [[ "$MODE" == "reconfigure" ]]; then
  BASE_DIR="${BASE_DIR:-${DOCUMENTS_DIR:-$TARGET_HOME}}"
  TARGET_REPO_DIR="${ORGANIZER_DIR:-$BASE_DIR/organizador-producao}"
  TARGET_BACKUP_DIR="${BACKUP_DIR:-$BASE_DIR/backup_database}"
fi

detect_ips() {
  mapfile -t DETECTED_IPS < <(ip -o -4 addr show up scope global | awk '{print $4}' | cut -d/ -f1 | sort -u)
  local route_ip
  route_ip="$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}')"
  if [[ -n "$route_ip" ]]; then
    DEFAULT_IP="$route_ip"
  elif [[ ${#DETECTED_IPS[@]} -gt 0 ]]; then
    DEFAULT_IP="${DETECTED_IPS[0]}"
  else
    fail "nenhum IPv4 global detectado na maquina."
  fi
}

prompt_yes_no() {
  local question="$1"
  local default_value="$2"
  local default_text="y/N"
  if is_true "$default_value"; then
    default_text="Y/n"
  fi

  if [[ "$NON_INTERACTIVE" == true ]]; then
    if is_true "$default_value"; then
      echo "true"
    else
      echo "false"
    fi
    return
  fi

  while true; do
    read -rp "$question [$default_text]: " answer
    answer="${answer:-}"
    if [[ -z "$answer" ]]; then
      if is_true "$default_value"; then
        echo "true"
      else
        echo "false"
      fi
      return
    fi
    if [[ "${answer,,}" =~ ^(y|yes|s|sim|1|true)$ ]]; then
      echo "true"
      return
    fi
    if [[ "${answer,,}" =~ ^(n|no|nao|0|false)$ ]]; then
      echo "false"
      return
    fi
    prompt_msg "Resposta invalida. Use y/n."
  done
}

prompt_text() {
  local question="$1"
  local default_value="$2"

  if [[ "$NON_INTERACTIVE" == true ]]; then
    echo "$default_value"
    return
  fi

  while true; do
    read -rp "$question [ENTER para '${default_value}']: " answer
    answer="${answer:-$default_value}"
    if [[ -n "$answer" ]]; then
      echo "$answer"
      return
    fi
    prompt_msg "Valor invalido."
  done
}

prompt_positive_integer() {
  local question="$1"
  local default_value="$2"

  if [[ "$NON_INTERACTIVE" == true ]]; then
    echo "$default_value"
    return
  fi

  while true; do
    read -rp "$question [ENTER para '${default_value}']: " answer
    answer="${answer:-$default_value}"
    if [[ "$answer" =~ ^[1-9][0-9]*$ ]]; then
      echo "$answer"
      return
    fi
    prompt_msg "Valor invalido. Use inteiro positivo (>= 1)."
  done
}

prompt_ip_selection() {
  local chosen_default="$1"
  local selected="$chosen_default"

  if [[ "$NON_INTERACTIVE" == true ]]; then
    echo "$selected"
    return
  fi

  prompt_msg ""
  prompt_msg "Hostname detectado: $HOSTNAME_VALUE"
  prompt_msg "IP sugerido: $chosen_default"
  prompt_msg "IPs disponiveis:"
  local idx=1
  for ip_addr in "${DETECTED_IPS[@]}"; do
    if [[ "$ip_addr" == "$chosen_default" ]]; then
      printf "  %d) %s (sugerido)\n" "$idx" "$ip_addr" >&2
    else
      printf "  %d) %s\n" "$idx" "$ip_addr" >&2
    fi
    ((idx++))
  done
  prompt_msg "  m) informar IP manualmente"
  prompt_msg ""

  while true; do
    read -rp "Escolha o IP [ENTER para sugerido]: " opt
    opt="${opt:-}"
    if [[ -z "$opt" ]]; then
      echo "$chosen_default"
      return
    fi
    if [[ "${opt,,}" == "m" ]]; then
      read -rp "Digite o IP: " manual_ip
      if [[ "$manual_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
        echo "$manual_ip"
        return
      fi
      prompt_msg "IP invalido."
      continue
    fi
    if [[ "$opt" =~ ^[0-9]+$ && "$opt" -ge 1 && "$opt" -le "${#DETECTED_IPS[@]}" ]]; then
      echo "${DETECTED_IPS[$((opt-1))]}"
      return
    fi
    prompt_msg "Opcao invalida."
  done
}

is_valid_timer_spec() {
  local spec="$1"
  local spec_type spec_value

  [[ -n "$spec" && "$spec" != *$'\n'* ]] || return 1
  [[ "$spec" == "disabled" ]] && return 0

  IFS='|' read -r spec_type spec_value <<< "$spec"
  [[ -n "${spec_type:-}" && -n "${spec_value:-}" ]] || return 1
  [[ "$spec_type" == "calendar" || "$spec_type" == "interval" ]]
}

prompt_schedule() {
  local label="$1"
  local current_spec="$2"
  local fallback_spec="$3"
  local spec="${current_spec:-$fallback_spec}"

  if ! is_valid_timer_spec "$spec"; then
    prompt_msg "Spec atual invalido para '$label'; usando padrao: $fallback_spec"
    spec="$fallback_spec"
  fi

  if [[ "$NON_INTERACTIVE" == true ]]; then
    echo "$spec"
    return
  fi

  prompt_msg ""
  prompt_msg "Configuracao de periodicidade para: $label"
  prompt_msg "Atual: $spec"
  prompt_msg "  1) Desabilitado"
  prompt_msg "  2) Diario 02:00"
  prompt_msg "  3) Semanal (domingo 04:00)"
  prompt_msg "  4) A cada 6 horas"
  prompt_msg "  5) A cada 12 horas"
  prompt_msg "  6) OnCalendar customizado"
  prompt_msg "  7) Intervalo customizado (OnUnitActiveSec)"

  while true; do
    read -rp "Opcao [ENTER para manter atual]: " option
    option="${option:-}"
    if [[ -z "$option" ]]; then
      echo "$spec"
      return
    fi
    case "$option" in
      1) echo "disabled"; return ;;
      2) echo "calendar|*-*-* 02:00:00"; return ;;
      3) echo "calendar|Sun *-*-* 04:00:00"; return ;;
      4) echo "interval|6h"; return ;;
      5) echo "interval|12h"; return ;;
      6)
        read -rp "OnCalendar (ex.: Mon..Fri *-*-* 03:30:00): " calendar_expr
        [[ -n "$calendar_expr" ]] || { prompt_msg "Valor vazio."; continue; }
        echo "calendar|$calendar_expr"
        return
        ;;
      7)
        read -rp "OnUnitActiveSec (ex.: 30min, 2h, 1d): " interval_expr
        [[ -n "$interval_expr" ]] || { prompt_msg "Valor vazio."; continue; }
        echo "interval|$interval_expr"
        return
        ;;
      *) prompt_msg "Opcao invalida." ;;
    esac
  done
}

write_env_var() {
  printf '%s=%q\n' "$1" "$2"
}

write_env_file() {
  mkdir -p "$(dirname "$ENV_FILE")"
  {
    echo "# Arquivo gerado pelo installer do Organizer."
    echo "# Edite apenas se souber o impacto operacional."
    write_env_var TARGET_USER "$TARGET_USER"
    write_env_var USER_HOME "$TARGET_HOME"
    write_env_var BASE_DIR "$BASE_DIR"
    write_env_var DOCUMENTS_DIR "$BASE_DIR"
    write_env_var ORGANIZER_DIR "$TARGET_REPO_DIR"
    write_env_var BACKUP_DIR "$TARGET_BACKUP_DIR"
    write_env_var COMPOSE_FILE "$TARGET_REPO_DIR/docker-compose.yml"
    write_env_var HOSTNAME_VALUE "$HOSTNAME_VALUE"
    write_env_var SERVER_HOST "$SERVER_HOST"
    write_env_var MINIO_HOST "$MINIO_HOST"
    write_env_var APP_DXF_ANALYSIS_IMAGE_BASE_URL "$APP_DXF_ANALYSIS_IMAGE_BASE_URL"
    write_env_var MINIO_ENDPOINT "$MINIO_ENDPOINT"
    write_env_var MINIO_BUCKET "$MINIO_BUCKET"
    write_env_var MINIO_DOCKER_NETWORK "$MINIO_DOCKER_NETWORK"
    write_env_var POSTGRES_CONTAINER_NAME "$POSTGRES_CONTAINER_NAME"
    write_env_var POSTGRES_USER "$POSTGRES_USER"
    write_env_var POSTGRES_DB "$POSTGRES_DB"
    write_env_var POSTGRES_PASSWORD "$POSTGRES_PASSWORD"
    write_env_var MAX_BACKUPS "$MAX_BACKUPS"
    write_env_var REMOTE_BACKUP_ENABLED "$REMOTE_BACKUP_ENABLED"
    write_env_var REMOTE_HOST "$REMOTE_HOST"
    write_env_var REMOTE_USER "$REMOTE_USER"
    write_env_var REMOTE_DIR "$REMOTE_DIR"
    write_env_var SSH_KEY "$SSH_KEY"
    write_env_var STARTUP_ENABLED "$STARTUP_ENABLED"
    write_env_var BACKUP_TIMER_SPEC "$BACKUP_TIMER_SPEC"
    write_env_var RESTART_TIMER_SPEC "$RESTART_TIMER_SPEC"
  } > "$ENV_FILE"
  chmod 640 "$ENV_FILE"
}

sync_repository() {
  mkdir -p "$BASE_DIR"
  if [[ "$(realpath "$SOURCE_REPO_DIR")" == "$(realpath -m "$TARGET_REPO_DIR")" ]]; then
    log "Repositorio ja esta no destino: $TARGET_REPO_DIR"
    return
  fi

  log "Sincronizando repositorio para '$TARGET_REPO_DIR'..."
  mkdir -p "$TARGET_REPO_DIR"
  rsync -a --delete \
    --exclude 'node_modules/' \
    --exclude 'target/' \
    --exclude 'organizer-front/node_modules/' \
    --exclude 'organizer-front/dist/' \
    --exclude '.angular/' \
    "$SOURCE_REPO_DIR/" "$TARGET_REPO_DIR/"
  chown -R "$TARGET_USER:$TARGET_USER" "$TARGET_REPO_DIR"
}

install_tools() {
  mkdir -p "$TARGET_BACKUP_DIR"
  install -m 750 -o "$TARGET_USER" -g "$TARGET_USER" \
    "$TARGET_REPO_DIR/scripts/ops/backup_postgres.sh" "$TARGET_BACKUP_DIR/backup_postgres.sh"
  install -m 750 -o "$TARGET_USER" -g "$TARGET_USER" \
    "$TARGET_REPO_DIR/scripts/ops/restore_backup.sh" "$TARGET_BACKUP_DIR/restore_backup.sh"
  if [[ -f "$TARGET_REPO_DIR/scripts/ops/README.md" ]]; then
    install -m 640 -o "$TARGET_USER" -g "$TARGET_USER" \
      "$TARGET_REPO_DIR/scripts/ops/README.md" "$TARGET_BACKUP_DIR/BACKUP_GUIDE.md"
  fi

  chmod 750 "$TARGET_REPO_DIR/update-organizer" "$TARGET_REPO_DIR/installer/install-organizer.sh"
  ln -sfn "$TARGET_REPO_DIR/update-organizer" /usr/local/bin/update-organizer
  ln -sfn "$TARGET_BACKUP_DIR/backup_postgres.sh" /usr/local/bin/organizer-backup
  ln -sfn "$TARGET_BACKUP_DIR/restore_backup.sh" /usr/local/bin/organizer-restore
  ln -sfn "$TARGET_REPO_DIR/installer/install-organizer.sh" /usr/local/bin/organizer-installer

  chown -R "$TARGET_USER:$TARGET_USER" "$TARGET_BACKUP_DIR"
}

ensure_user_in_docker_group() {
  if ! id -nG "$TARGET_USER" | grep -qw docker; then
    usermod -aG docker "$TARGET_USER"
    DOCKER_GROUP_ADDED=true
  else
    DOCKER_GROUP_ADDED=false
  fi
}

validate_remote_backup_access() {
  if ! is_true "$REMOTE_BACKUP_ENABLED"; then
    log "Validacao de backup remoto: desabilitado."
    return
  fi

  need ssh
  [[ -n "$REMOTE_HOST" && -n "$REMOTE_USER" && -n "$REMOTE_DIR" ]] || \
    fail "backup remoto habilitado, mas REMOTE_HOST/REMOTE_USER/REMOTE_DIR estao incompletos."

  local remote_target="${REMOTE_USER}@${REMOTE_HOST}"
  local -a ssh_cmd=(ssh -o BatchMode=yes -o ConnectTimeout=8 -o StrictHostKeyChecking=accept-new)
  local remote_cmd
  remote_cmd="mkdir -p \"$REMOTE_DIR\" && test -w \"$REMOTE_DIR\""

  if [[ -n "$SSH_KEY" ]]; then
    sudo -u "$TARGET_USER" test -r "$SSH_KEY" || \
      fail "SSH_KEY '$SSH_KEY' nao pode ser lida por '$TARGET_USER'."
    ssh_cmd+=(-i "$SSH_KEY")
  fi

  log "Validando backup remoto em ${remote_target}:${REMOTE_DIR}..."
  if ! sudo -u "$TARGET_USER" "${ssh_cmd[@]}" "$remote_target" "$remote_cmd" >/dev/null 2>&1; then
    fail "falha na validacao do backup remoto. Configure chave SSH sem senha para '$TARGET_USER' e teste: ssh ${remote_target}"
  fi
  log "Validacao de backup remoto: OK."
}

write_stack_service() {
  cat > "$SYSTEMD_DIR/organizer-stack.service" <<EOF
[Unit]
Description=Organizer stack (Docker Compose)
After=network-online.target docker.service
Wants=network-online.target docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
User=$TARGET_USER
Group=$TARGET_USER
WorkingDirectory=$TARGET_REPO_DIR
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/docker compose -f $TARGET_REPO_DIR/docker-compose.yml --project-directory $TARGET_REPO_DIR up -d
ExecStop=/usr/bin/docker compose -f $TARGET_REPO_DIR/docker-compose.yml --project-directory $TARGET_REPO_DIR down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
EOF
}

write_backup_service() {
  cat > "$SYSTEMD_DIR/organizer-backup.service" <<EOF
[Unit]
Description=Organizer backup job (Postgres + MinIO)
After=docker.service organizer-stack.service
Wants=docker.service

[Service]
Type=oneshot
User=$TARGET_USER
Group=$TARGET_USER
EnvironmentFile=$ENV_FILE
ExecStart=$TARGET_BACKUP_DIR/backup_postgres.sh
EOF
}

write_restart_service() {
  cat > "$SYSTEMD_DIR/organizer-restart.service" <<EOF
[Unit]
Description=Organizer scheduled restart (docker compose restart)
After=docker.service organizer-stack.service
Wants=docker.service

[Service]
Type=oneshot
User=$TARGET_USER
Group=$TARGET_USER
WorkingDirectory=$TARGET_REPO_DIR
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/docker compose -f $TARGET_REPO_DIR/docker-compose.yml --project-directory $TARGET_REPO_DIR restart
EOF
}

write_timer_unit() {
  local unit_name="$1"
  local description="$2"
  local spec="$3"
  local timer_file="$SYSTEMD_DIR/${unit_name}.timer"

  if [[ "$spec" == "disabled" ]]; then
    systemctl disable --now "${unit_name}.timer" >/dev/null 2>&1 || true
    rm -f "$timer_file"
    return
  fi

  IFS='|' read -r spec_type spec_value <<< "$spec"
  [[ -n "${spec_type:-}" && -n "${spec_value:-}" ]] || fail "timer spec invalido para $unit_name: '$spec'"

  {
    echo "[Unit]"
    echo "Description=$description"
    echo
    echo "[Timer]"
    echo "Persistent=true"
    echo "Unit=${unit_name}.service"
    if [[ "$spec_type" == "calendar" ]]; then
      echo "OnCalendar=$spec_value"
    elif [[ "$spec_type" == "interval" ]]; then
      echo "OnBootSec=10min"
      echo "OnUnitActiveSec=$spec_value"
    else
      fail "tipo de timer desconhecido '$spec_type' para $unit_name"
    fi
    echo
    echo "[Install]"
    echo "WantedBy=timers.target"
  } > "$timer_file"
}

apply_systemd() {
  write_stack_service
  write_backup_service
  write_restart_service
  write_timer_unit "organizer-backup" "Organizer backup schedule" "$BACKUP_TIMER_SPEC"
  write_timer_unit "organizer-restart" "Organizer restart schedule" "$RESTART_TIMER_SPEC"

  systemctl daemon-reload

  if is_true "$STARTUP_ENABLED"; then
    systemctl enable --now organizer-stack.service
  else
    systemctl disable organizer-stack.service >/dev/null 2>&1 || true
  fi

  if [[ "$BACKUP_TIMER_SPEC" == "disabled" ]]; then
    systemctl disable --now organizer-backup.timer >/dev/null 2>&1 || true
  else
    systemctl enable --now organizer-backup.timer
  fi

  if [[ "$RESTART_TIMER_SPEC" == "disabled" ]]; then
    systemctl disable --now organizer-restart.timer >/dev/null 2>&1 || true
  else
    systemctl enable --now organizer-restart.timer
  fi
}

show_summary() {
  echo
  echo "Instalacao/configuracao concluida."
  echo "--------------------------------"
  echo "Usuario alvo:              $TARGET_USER"
  echo "Hostname:                  $HOSTNAME_VALUE"
  echo "IP configurado (SERVER):   $SERVER_HOST"
  echo "Repositorio:               $TARGET_REPO_DIR"
  echo "Backup dir:                $TARGET_BACKUP_DIR"
  echo "Arquivo de ambiente:       $ENV_FILE"
  echo "Startup habilitado:        $STARTUP_ENABLED"
  echo "Backup schedule:           $BACKUP_TIMER_SPEC"
  echo "Retencao backups:          $MAX_BACKUPS"
  echo "Backup remoto habilitado:  $REMOTE_BACKUP_ENABLED"
  if is_true "$REMOTE_BACKUP_ENABLED"; then
    echo "Destino backup remoto:     ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}"
    if [[ -n "$SSH_KEY" ]]; then
      echo "Chave SSH remota:          $SSH_KEY"
    else
      echo "Chave SSH remota:          padrao do usuario ($TARGET_USER)"
    fi
  fi
  echo "Restart schedule:          $RESTART_TIMER_SPEC"
  echo
  echo "Comandos uteis:"
  echo "  organizer-backup                    # manual (tambem roda via timer)"
  echo "  update-organizer                    # manual"
  echo "  organizer-restore --choose          # manual"
  echo "  sudo organizer-installer --reconfigure-schedules --user $TARGET_USER"
  echo
  echo "Status rapido:"
  systemctl --no-pager --full status organizer-stack.service 2>/dev/null | sed -n '1,4p' || true
  systemctl --no-pager --full status organizer-backup.timer 2>/dev/null | sed -n '1,4p' || true
  systemctl --no-pager --full status organizer-restart.timer 2>/dev/null | sed -n '1,4p' || true
  echo
  echo "Ferramentas manuais (status/local/uso):"
  if [[ -x /usr/local/bin/update-organizer ]]; then
    echo "  update-organizer: OK"
    echo "    atalho: /usr/local/bin/update-organizer"
    echo "    destino: $(readlink -f /usr/local/bin/update-organizer)"
    echo "    uso: update-organizer"
  else
    echo "  update-organizer: AUSENTE (/usr/local/bin/update-organizer)"
  fi
  if [[ -x /usr/local/bin/organizer-restore ]]; then
    echo "  organizer-restore: OK"
    echo "    atalho: /usr/local/bin/organizer-restore"
    echo "    destino: $(readlink -f /usr/local/bin/organizer-restore)"
    echo "    uso: organizer-restore --choose"
  else
    echo "  organizer-restore: AUSENTE (/usr/local/bin/organizer-restore)"
  fi

  if [[ "${DOCKER_GROUP_ADDED:-false}" == "true" ]]; then
    echo
    echo "Aviso: '$TARGET_USER' foi adicionado ao grupo docker."
    echo "Efetue logout/login para comandos manuais com docker sem sudo."
  fi
}

configure_runtime_values() {
  detect_ips

  local default_max_backups="$MAX_BACKUPS"
  local default_remote_enabled="$REMOTE_BACKUP_ENABLED"
  local default_remote_host="$REMOTE_HOST"
  local default_remote_user="$REMOTE_USER"
  local default_remote_dir="$REMOTE_DIR"
  if [[ "$MODE" == "install" ]]; then
    default_max_backups="2"
    default_remote_enabled="true"
    default_remote_host="192.168.10.31"
    default_remote_user="monitor"
    default_remote_dir="/home/${default_remote_user}/backups_postgres"
  fi

  local base_ip="${SERVER_HOST:-$DEFAULT_IP}"
  local chosen_ip
  chosen_ip="$(prompt_ip_selection "$base_ip")"
  SERVER_HOST="$chosen_ip"
  MINIO_HOST="$chosen_ip"
  APP_DXF_ANALYSIS_IMAGE_BASE_URL="http://${chosen_ip}:9000/facas-renders"
  MINIO_ENDPOINT="http://${chosen_ip}:9000"

  STARTUP_ENABLED="$(prompt_yes_no "Habilitar startup automatico no boot?" "${STARTUP_ENABLED:-true}")"
  BACKUP_TIMER_SPEC="$(prompt_schedule "backup automatico" "$BACKUP_TIMER_SPEC" "calendar|*-*-* 02:00:00")"
  RESTART_TIMER_SPEC="$(prompt_schedule "restart automatico da aplicacao" "$RESTART_TIMER_SPEC" "disabled")"
  MAX_BACKUPS="$(prompt_positive_integer "Quantidade maxima de backups local/remoto" "$default_max_backups")"
  REMOTE_BACKUP_ENABLED="$(prompt_yes_no "Habilitar backup remoto?" "$default_remote_enabled")"
  if is_true "$REMOTE_BACKUP_ENABLED"; then
    REMOTE_HOST="$(prompt_text "Host remoto" "$default_remote_host")"
    REMOTE_USER="$(prompt_text "Usuario remoto" "$default_remote_user")"
    REMOTE_DIR="$(prompt_text "Diretorio remoto para backups" "$default_remote_dir")"
  fi
}

if [[ "$MODE" == "install" ]]; then
  log "Modo: instalacao completa"
  sync_repository
  ensure_user_in_docker_group
  install_tools
  configure_runtime_values
  validate_remote_backup_access
  write_env_file
  apply_systemd
  show_summary
  exit 0
fi

if [[ "$MODE" == "reconfigure" ]]; then
  log "Modo: reconfiguracao de startup/timers"
  [[ -r "$ENV_FILE" ]] || fail "arquivo '$ENV_FILE' nao encontrado. Rode a instalacao completa primeiro."
  configure_runtime_values
  validate_remote_backup_access
  write_env_file
  apply_systemd
  show_summary
  exit 0
fi

fail "modo desconhecido: $MODE"
