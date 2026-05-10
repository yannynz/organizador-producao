#!/usr/bin/env bash
set -euo pipefail

FW_DIR="${FW_DIR:-/home/ynz/Documents/FileWatcherApp}"
ORG_HOST="${ORG_HOST:-${SERVER_HOST:-$(hostname -I | awk '{print $1}')}}"
RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"

mkdir -p \
  "$FW_DIR/test_env/Laser" \
  "$FW_DIR/test_env/FacasDXF" \
  "$FW_DIR/test_env/Dobras" \
  "$FW_DIR/test_env/Ops" \
  "$FW_DIR/artifacts/renders" \
  "$FW_DIR/artifacts/cache"

export DOTNET_ENVIRONMENT="${DOTNET_ENVIRONMENT:-Development}"
export RabbitMq__HostName="$ORG_HOST"
export RabbitMq__Port="$RABBITMQ_PORT"
export RabbitMq__Queue="${RabbitMq__Queue:-filewatcher.rpc.ping}"
export DXFAnalysis__RabbitMq__HostName="$ORG_HOST"
export DXFAnalysis__RabbitMq__Port="$RABBITMQ_PORT"
export DXFAnalysis__RabbitMq__UserName="${DXFAnalysis__RabbitMq__UserName:-guest}"
export DXFAnalysis__RabbitMq__Password="${DXFAnalysis__RabbitMq__Password:-guest}"
export DXFAnalysis__RabbitMq__VirtualHost="${DXFAnalysis__RabbitMq__VirtualHost:-/}"
export DXFAnalysis__RabbitQueueRequest="${DXFAnalysis__RabbitQueueRequest:-facas.analysis.request}"
export DXFAnalysis__ImageStorage__Enabled="${DXFAnalysis__ImageStorage__Enabled:-true}"
export DXFAnalysis__ImageStorage__Provider="${DXFAnalysis__ImageStorage__Provider:-s3}"
export DXFAnalysis__ImageStorage__Endpoint="${DXFAnalysis__ImageStorage__Endpoint:-http://$ORG_HOST:9000}"
export DXFAnalysis__ImageStorage__PublicBaseUrl="${DXFAnalysis__ImageStorage__PublicBaseUrl:-http://$ORG_HOST/facas-renders}"
export DXFAnalysis__ImageStorage__Bucket="${DXFAnalysis__ImageStorage__Bucket:-facas-renders}"
export DXFAnalysis__WatchFolder="${DXFAnalysis__WatchFolder:-$FW_DIR/test_env/FacasDXF}"
export DXFAnalysis__OutputImageFolder="${DXFAnalysis__OutputImageFolder:-$FW_DIR/artifacts/renders}"
export DXFAnalysis__CacheFolder="${DXFAnalysis__CacheFolder:-$FW_DIR/artifacts/cache}"
export FileWatcher__LaserDirectory="${FileWatcher__LaserDirectory:-$FW_DIR/test_env/Laser}"
export FileWatcher__FacasDirectory="${FileWatcher__FacasDirectory:-$FW_DIR/test_env/FacasDXF}"
export FileWatcher__DobrasDirectory="${FileWatcher__DobrasDirectory:-$FW_DIR/test_env/Dobras}"
export FileWatcher__OpsDirectory="${FileWatcher__OpsDirectory:-$FW_DIR/test_env/Ops}"

cd "$FW_DIR"
exec dotnet run --project FileWatcherApp.csproj
