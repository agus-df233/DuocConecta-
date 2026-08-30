#!/usr/bin/env bash
#
# aws.sh — despliegue de DuocConecta en AWS Academy Learner Lab.
#
# Un solo script con subcomandos, en vez de seis archivos que se desincronizan:
#
#   ./infra/aws.sh verificar   Qué servicios habilita el lab y si LabRole alcanza. Correr primero.
#   ./infra/aws.sh crear       Crea ECR, cluster, security groups, RDS, ALB y API Gateway. Idempotente.
#   ./infra/aws.sh build       Compila la imagen de un servicio y la sube a ECR.
#   ./infra/aws.sh desplegar   Registra la task definition y actualiza el servicio de ECS.
#   ./infra/aws.sh iniciar     Levanta la tarea, espera el health y muestra las URLs.
#   ./infra/aws.sh apagar      Baja la tarea a cero. IMPORTANTE: correrlo al terminar de trabajar.
#   ./infra/aws.sh urls        Muestra las URLs del ALB y del API Gateway.
#
# Las credenciales del lab van en ~/.aws/credentials (panel "AWS Details"), nunca en el repo:
# rotan en cada sesión.

set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
PROYECTO="duocconecta"
CLUSTER="$PROYECTO"
SERVICIO_ECS="$PROYECTO"
FAMILIA="$PROYECTO"
SERVICIOS=(ms-usuarios bff-web ms-proyectos)
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Puerto y ruta de cada servicio. Se resuelven con funciones y no con arreglos asociativos
# porque macOS trae bash 3.2, que no los soporta: en esa versión el script ni siquiera arranca.
puerto_de() {
  case "$1" in
    bff-web)      echo 8080 ;;
    ms-usuarios)  echo 8081 ;;
    ms-proyectos) echo 8082 ;;
  esac
}

# Path por el que el ALB enruta hacia cada servicio.
ruta_de() {
  case "$1" in
    bff-web)      echo "/api/v1/bff*" ;;
    ms-usuarios)  echo "/api/v1/usuarios*" ;;
    ms-proyectos) echo "/api/v1/proyectos*" ;;
  esac
}

azul()  { printf '\033[1;34m%b\033[0m\n' "$*"; }
ok()    { printf '  \033[32m✓\033[0m %s\n' "$*"; }
falla() { printf '  \033[31m✗\033[0m %s\n' "$*"; }
aviso() { printf '  \033[33m!\033[0m %s\n' "$*"; }

# Carga .env si existe, para tomar los identificadores de Azure sin pedirlos cada vez.
[[ -f "$RAIZ/.env" ]] && set -a && source "$RAIZ/.env" && set +a

cuenta() { aws sts get-caller-identity --query Account --output text; }
registro() { echo "$(cuenta).dkr.ecr.$REGION.amazonaws.com"; }

# ---------------------------------------------------------------------------
# verificar — qué permite realmente el lab. Correr esto antes que nada.
# ---------------------------------------------------------------------------
cmd_verificar() {
  azul "Región: $REGION"
  aws sts get-caller-identity --output table || { falla "Sin credenciales. Pegá el bloque de AWS Details en ~/.aws/credentials"; exit 1; }

  azul "\nRol LabRole (obligatorio: en el lab no se pueden crear roles IAM)"
  if aws iam get-role --role-name LabRole >/dev/null 2>&1; then
    ok "LabRole existe"
  else
    falla "LabRole no existe. Sin él las tareas de Fargate no pueden arrancar."
  fi

  azul "\nServicios que usa el despliegue"
  probar() { # $1 = nombre legible, $2... = comando
    local nombre="$1"; shift
    if "$@" >/dev/null 2>&1; then ok "$nombre"; else falla "$nombre — NO disponible"; fi
  }
  probar "ECR"             aws ecr describe-repositories --max-items 1
  probar "ECS"             aws ecs list-clusters --max-items 1
  probar "RDS"             aws rds describe-db-instances --max-items 1
  probar "ALB"             aws elbv2 describe-load-balancers --page-size 1
  probar "Secrets Manager" aws secretsmanager list-secrets --max-results 1
  probar "API Gateway"     aws apigatewayv2 get-apis --max-results 1
  probar "CloudWatch Logs" aws logs describe-log-groups --limit 1

  azul "\nRecordatorio de costo"
  aviso "ALB y RDS no se apagan solos: 'aws.sh apagar' baja las tareas, pero el ALB sigue cobrando."
  aviso "Mirá el crédito consumido en el panel del Learner Lab todos los días."
}

# ---------------------------------------------------------------------------
# crear — infraestructura. Idempotente: se puede correr las veces que haga falta.
# ---------------------------------------------------------------------------
cmd_crear() {
  local acc; acc=$(cuenta)

  azul "1/6 · Repositorios de ECR"
  for s in "${SERVICIOS[@]}"; do
    aws ecr describe-repositories --repository-names "$PROYECTO/$s" >/dev/null 2>&1 \
      || aws ecr create-repository --repository-name "$PROYECTO/$s" >/dev/null
    ok "$PROYECTO/$s"
  done

  azul "2/6 · Cluster de ECS y grupo de logs"
  aws ecs describe-clusters --clusters "$CLUSTER" --query 'clusters[0].status' --output text 2>/dev/null | grep -q ACTIVE \
    || aws ecs create-cluster --cluster-name "$CLUSTER" >/dev/null
  # Sin --capacity-providers a propósito: ese flag exige permisos sobre el service-linked role
  # que el Learner Lab no concede. Fargate funciona igual porque el servicio se crea
  # más abajo con --launch-type FARGATE.
  aws logs create-log-group --log-group-name "/ecs/$PROYECTO" 2>/dev/null || true
  ok "cluster $CLUSTER"

  azul "3/6 · Red y security groups"
  local vpc subnets sg_alb sg_tareas sg_rds
  vpc=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)
  subnets=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$vpc" --query 'Subnets[].SubnetId' --output text | tr '\t' ',')
  ok "VPC $vpc"

  crear_sg() { # $1 = nombre, $2 = descripción
    local id
    id=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$1" "Name=vpc-id,Values=$vpc" \
         --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null)
    if [[ "$id" == "None" || -z "$id" ]]; then
      id=$(aws ec2 create-security-group --group-name "$1" --description "$2" --vpc-id "$vpc" --query GroupId --output text)
    fi
    echo "$id"
  }
  sg_alb=$(crear_sg "$PROYECTO-alb" "Entrada HTTP publica al ALB de DuocConecta")
  sg_tareas=$(crear_sg "$PROYECTO-tareas" "Tareas de Fargate; solo aceptan trafico del ALB")
  sg_rds=$(crear_sg "$PROYECTO-rds" "PostgreSQL; solo acepta trafico de las tareas")

  # El ALB acepta HTTP de cualquiera; las tareas solo del ALB; la base solo de las tareas.
  aws ec2 authorize-security-group-ingress --group-id "$sg_alb" --protocol tcp --port 80 --cidr 0.0.0.0/0 >/dev/null 2>&1 || true
  for p in 8080 8081 8082; do
    aws ec2 authorize-security-group-ingress --group-id "$sg_tareas" --protocol tcp --port "$p" --source-group "$sg_alb" >/dev/null 2>&1 || true
  done
  aws ec2 authorize-security-group-ingress --group-id "$sg_rds" --protocol tcp --port 5432 --source-group "$sg_tareas" >/dev/null 2>&1 || true
  ok "SGs: alb=$sg_alb tareas=$sg_tareas rds=$sg_rds"

  azul "4/6 · Base de datos RDS"
  # --manage-master-user-password deja la contraseña en Secrets Manager: nunca pasa por acá.
  if ! aws rds describe-db-instances --db-instance-identifier "$PROYECTO-db" >/dev/null 2>&1; then
    aws rds create-db-instance \
      --db-instance-identifier "$PROYECTO-db" \
      --db-name duocconecta --engine postgres --engine-version 16.15 \
      --db-instance-class db.t3.micro --allocated-storage 20 \
      --master-username duocconecta --manage-master-user-password \
      --vpc-security-group-ids "$sg_rds" \
      --no-publicly-accessible --backup-retention-period 0 --no-multi-az >/dev/null
    aviso "RDS creándose (tarda ~8 min). El resto sigue."
  fi
  ok "$PROYECTO-db"

  azul "5/6 · ALB y target groups"
  local alb_arn listener_arn
  alb_arn=$(aws elbv2 describe-load-balancers --names "$PROYECTO-alb" --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || true)
  if [[ -z "$alb_arn" || "$alb_arn" == "None" ]]; then
    alb_arn=$(aws elbv2 create-load-balancer --name "$PROYECTO-alb" --type application --scheme internet-facing \
      --subnets ${subnets//,/ } --security-groups "$sg_alb" --query 'LoadBalancers[0].LoadBalancerArn' --output text)
  fi

  # Un target group por servicio: así se puede pegar directo a /api/v1/usuarios y ver el 401,
  # que es la demostración de que cada capa valida el token por su cuenta.
  local prioridad=10
  for s in "${SERVICIOS[@]}"; do
    local tg
    tg=$(aws elbv2 describe-target-groups --names "$PROYECTO-$s" --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)
    if [[ -z "$tg" || "$tg" == "None" ]]; then
      tg=$(aws elbv2 create-target-group --name "$PROYECTO-$s" --protocol HTTP --port "$(puerto_de "$s")" \
        --vpc-id "$vpc" --target-type ip --health-check-path /actuator/health \
        --health-check-interval-seconds 30 --healthy-threshold-count 2 \
        --query 'TargetGroups[0].TargetGroupArn' --output text)
    fi
    ok "target group $PROYECTO-$s → :$(puerto_de "$s")"
  done

  # El listener manda por defecto al BFF, que es lo único que el frontend conoce.
  local tg_bff; tg_bff=$(aws elbv2 describe-target-groups --names "$PROYECTO-bff-web" --query 'TargetGroups[0].TargetGroupArn' --output text)
  listener_arn=$(aws elbv2 describe-listeners --load-balancer-arn "$alb_arn" --query 'Listeners[0].ListenerArn' --output text 2>/dev/null || true)
  if [[ -z "$listener_arn" || "$listener_arn" == "None" ]]; then
    listener_arn=$(aws elbv2 create-listener --load-balancer-arn "$alb_arn" --protocol HTTP --port 80 \
      --default-actions "Type=forward,TargetGroupArn=$tg_bff" --query 'Listeners[0].ListenerArn' --output text)
  fi
  for s in ms-usuarios ms-proyectos; do
    local tg; tg=$(aws elbv2 describe-target-groups --names "$PROYECTO-$s" --query 'TargetGroups[0].TargetGroupArn' --output text)
    aws elbv2 create-rule --listener-arn "$listener_arn" --priority "$prioridad" \
      --conditions "Field=path-pattern,Values=$(ruta_de "$s")" \
      --actions "Type=forward,TargetGroupArn=$tg" >/dev/null 2>&1 || true
    prioridad=$((prioridad + 10))
  done
  ok "ALB listo"

  azul "6/6 · API Gateway (capa API Manager)"
  local dns api_id
  dns=$(aws elbv2 describe-load-balancers --load-balancer-arns "$alb_arn" --query 'LoadBalancers[0].DNSName' --output text)
  api_id=$(aws apigatewayv2 get-apis --query "Items[?Name=='$PROYECTO'].ApiId" --output text)
  if [[ -z "$api_id" ]]; then
    api_id=$(aws apigatewayv2 create-api --name "$PROYECTO" --protocol-type HTTP \
      --target "http://$dns" --query ApiId --output text)
  fi
  ok "API Gateway $api_id"

  # Se guardan los identificadores para que 'desplegar' e 'iniciar' no los busquen de nuevo.
  cat > "$RAIZ/infra/.recursos" << EOV
VPC=$vpc
SUBNETS=$subnets
SG_TAREAS=$sg_tareas
ALB_ARN=$alb_arn
ALB_DNS=$dns
API_ID=$api_id
EOV
  aviso "Identificadores guardados en infra/.recursos (ignorado por git)"
  cmd_urls
}

# ---------------------------------------------------------------------------
# build — compila la imagen de un servicio y la sube a ECR.
# ---------------------------------------------------------------------------
cmd_build() {
  local s="${1:?Uso: aws.sh build <ms-usuarios|bff-web|ms-proyectos>}"
  [[ -d "$RAIZ/$s" ]] || { falla "El módulo $s todavía no existe en el repo"; exit 1; }
  local reg; reg=$(registro)

  aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$reg" >/dev/null
  # Se usa buildx y no 'docker build' porque hace falta fijar la arquitectura: Fargate corre
  # x86_64 y los Mac con chip M son ARM. Sin esto la imagen no arranca en la nube.
  # buildx construye y sube en un solo paso.
  docker buildx build --platform linux/amd64 --push \
    -f "$RAIZ/docker/Dockerfile" --build-arg "SERVICIO=$s" \
    -t "$reg/$PROYECTO/$s:latest" "$RAIZ"
  ok "$s publicado en ECR"
}

# ---------------------------------------------------------------------------
# desplegar — renderiza la task definition, la registra y actualiza el servicio.
# ---------------------------------------------------------------------------
cmd_desplegar() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || { falla "Falta correr 'aws.sh crear' primero"; exit 1; }
  local acc reg secreto endpoint tmp
  acc=$(cuenta); reg=$(registro)

  endpoint=$(aws rds describe-db-instances --db-instance-identifier "$PROYECTO-db" \
    --query 'DBInstances[0].Endpoint.Address' --output text)
  secreto=$(aws rds describe-db-instances --db-instance-identifier "$PROYECTO-db" \
    --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)
  [[ "$endpoint" == "None" ]] && { falla "RDS todavía no terminó de crearse"; exit 1; }

  # Sin estos valores el servicio arranca con un emisor vacío y rechaza todos los tokens,
  # pero el error recién aparece en los logs varios minutos después. Mejor fallar acá.
  [[ -z "${AZURE_TENANT_ID:-}" || -z "${AZURE_CLIENT_ID:-}" ]] && {
    falla "Faltan AZURE_TENANT_ID o AZURE_CLIENT_ID. Copiá .env.example a .env y completalos."
    exit 1
  }

  tmp=$(mktemp)  # archivo temporal: la task definition renderizada lleva ARNs y no va al repo
  sed -e "s|__ACCOUNT_ID__|$acc|g" -e "s|__REGISTRO__|$reg|g" -e "s|__TAG__|latest|g" \
      -e "s|__REGION__|$REGION|g" -e "s|__RDS_ENDPOINT__|$endpoint|g" \
      -e "s|__SECRETO_DB_ARN__|$secreto|g" \
      -e "s|__AZURE_TENANT_ID__|${AZURE_TENANT_ID:-}|g" \
      -e "s|__AZURE_CLIENT_ID__|${AZURE_CLIENT_ID:-}|g" \
      -e "s|__CORS_ORIGENES__|${CORS_ORIGENES:-http://localhost:5173}|g" \
      "$RAIZ/infra/task-definition.json" > "$tmp"

  # Se quita el bloque de comentarios (ECS rechaza campos que no conoce) y, si la imagen de
  # ms-proyectos todavía no está en ECR, se quita ese contenedor para no bloquear el despliegue.
  local filtro='del(._comentario)'
  if ! aws ecr describe-images --repository-name "$PROYECTO/ms-proyectos" --image-ids imageTag=latest >/dev/null 2>&1; then
    aviso "ms-proyectos aún no está en ECR: se despliega sin ese contenedor"
    filtro="$filtro | .containerDefinitions |= map(select(.name != \"ms-proyectos\"))"
  fi
  jq "$filtro" "$tmp" > "$tmp.json" && mv "$tmp.json" "$tmp"

  local rev; rev=$(aws ecs register-task-definition --cli-input-json "file://$tmp" \
    --query 'taskDefinition.taskDefinitionArn' --output text)
  rm -f "$tmp"
  ok "task definition registrada: ${rev##*/}"

  # Cada target group registrado apunta a su contenedor dentro de la misma tarea.
  local lb_args=()
  for s in "${SERVICIOS[@]}"; do
    local tg; tg=$(aws elbv2 describe-target-groups --names "$PROYECTO-$s" --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)
    [[ -z "$tg" || "$tg" == "None" ]] && continue
    [[ "$s" == "ms-proyectos" && ! -d "$RAIZ/ms-proyectos" ]] && continue
    lb_args+=("targetGroupArn=$tg,containerName=$s,containerPort=$(puerto_de "$s")")
  done

  # El período de gracia es imprescindible: el ALB empieza a chequear salud apenas registra la
  # tarea, pero Spring Boot tarda cerca de un minuto en levantar. Sin gracia, ECS mata la tarea
  # por "unhealthy" antes de que llegue a responder y el despliegue queda en un ciclo infinito.
  if aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICIO_ECS" \
       --query 'services[0].status' --output text 2>/dev/null | grep -q ACTIVE; then
    aws ecs update-service --cluster "$CLUSTER" --service "$SERVICIO_ECS" \
      --task-definition "$rev" --desired-count 1 --force-new-deployment >/dev/null
    ok "servicio actualizado"
  else
    aws ecs create-service --cluster "$CLUSTER" --service-name "$SERVICIO_ECS" \
      --task-definition "$rev" --desired-count 1 --launch-type FARGATE \
      --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_TAREAS],assignPublicIp=ENABLED}" \
      --health-check-grace-period-seconds 240 \
      --load-balancers "${lb_args[@]}" >/dev/null
    ok "servicio creado"
  fi
  cmd_urls
}

# ---------------------------------------------------------------------------
# iniciar / apagar — control de costo entre jornadas.
# ---------------------------------------------------------------------------
cmd_iniciar() {
  aws ecs update-service --cluster "$CLUSTER" --service "$SERVICIO_ECS" --desired-count 1 >/dev/null
  azul "Levantando la tarea (tarda ~2 min en pasar el health check)..."
  aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICIO_ECS" && ok "tarea estable"
  cmd_urls
}

cmd_apagar() {
  aws ecs update-service --cluster "$CLUSTER" --service "$SERVICIO_ECS" --desired-count 0 >/dev/null
  ok "Tareas en cero. El ALB y RDS siguen cobrando: borralos si no vas a trabajar por varios días."
}

cmd_urls() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || return 0
  azul "\nURLs"
  echo "  ALB (directo):   http://$ALB_DNS"
  echo "  API Gateway:     https://$API_ID.execute-api.$REGION.amazonaws.com"
  echo "  Health:          http://$ALB_DNS/actuator/health"
}

case "${1:-}" in
  verificar) cmd_verificar ;;
  crear)     cmd_crear ;;
  build)     cmd_build "${2:-}" ;;
  desplegar) cmd_desplegar ;;
  iniciar)   cmd_iniciar ;;
  apagar)    cmd_apagar ;;
  urls)      cmd_urls ;;
  *) sed -n '2,25p' "$0"; exit 1 ;;
esac
