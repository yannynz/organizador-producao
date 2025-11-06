#!/usr/bin/env bash
set -euo pipefail

FILE_NAME="NR120247SALINAS_VERMELHO.CNC"
LASER_DIR="/home/laser"
FACAS_OK_DIR="${LASER_DIR}/FACASOK"
DXF_SOURCE_DIR="/home/ynz/Documents"
DXF_TARGET_DIR="/home/dobras"

echo ">> Criando placeholder do CNC em ${LASER_DIR}..."
sudo touch "${LASER_DIR}/${FILE_NAME}"

echo ">> Movendo CNC para ${FACAS_OK_DIR}..."
sudo mv "${LASER_DIR}/${FILE_NAME}" "${FACAS_OK_DIR}/${FILE_NAME}"

echo ">> Copiando DXFs de ${DXF_SOURCE_DIR} para ${DXF_TARGET_DIR}..."
sudo cp -f "${DXF_SOURCE_DIR}"/*.dxf "${DXF_TARGET_DIR}/"

echo "Fluxo DXF disparado. Execute 'docker compose up --build' separadamente se necessário."
