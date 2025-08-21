#!/bin/bash
set -e  # Encerra o script imediatamente se qualquer comando falhar

# Diretório raiz do projeto
PROJECT_DIR="/home/recepcao/organizador-producao"

# Checar se o diretório raiz existe
if [ ! -d "$PROJECT_DIR" ]; then
  echo "Erro: Diretório do projeto não encontrado: $PROJECT_DIR"
  exit 1
fi

# Parar containers
cd "$PROJECT_DIR"
docker-compose down

# Build do Angular
FRONT_DIR="$PROJECT_DIR/organizer-front"

if [ ! -d "$FRONT_DIR" ]; then
  echo "Erro: Diretório do frontend não encontrado: $FRONT_DIR"
  exit 1
fi

cd "$FRONT_DIR"
ng build -c production

# Iniciar containers Docker
cd "$PROJECT_DIR"
docker-compose up --build -d

