#!/usr/bin/env bash
# Despliega nexabank-api a Cloud Run dentro del proyecto Firebase/GCP nexabank-core.
#
# Prerrequisitos (ver docs/FRONTEND_BACKEND_INTEGRATION_PLAN.md Fase B):
#   1. gcloud CLI instalado y autenticado (`gcloud auth login`).
#   2. Los siguientes secretos ya creados en Secret Manager (nombres exactos esperados
#      por este script — ajustalos abajo si usaste otros nombres):
#        firebase-project-id, firebase-client-email, firebase-private-key,
#        firebase-private-key-id, firebase-client-id,
#        twilio-account-sid, twilio-auth-token, twilio-from-number
#   3. Build desde la raíz del repo (este script debe ejecutarse desde NexaBank/).
#
# Uso: ./deploy-cloud-run.sh

set -euo pipefail

PROJECT_ID="nexabank-core"
REGION="us-central1"
SERVICE_NAME="nexabank-api"
ALLOWED_ORIGINS="https://nexabank-core.web.app,https://nexabank-core.firebaseapp.com"

gcloud config set project "${PROJECT_ID}"

# --source . construye la imagen en Cloud Build a partir del Dockerfile en la raíz —
# no requiere Docker instalado localmente.
gcloud run deploy "${SERVICE_NAME}" \
  --source . \
  --region "${REGION}" \
  --platform managed \
  --allow-unauthenticated \
  --min-instances 0 \
  --max-instances 2 \
  --memory 512Mi \
  --set-env-vars "SPRING_PROFILES_ACTIVE=prod,ALLOWED_ORIGINS=${ALLOWED_ORIGINS}" \
  --set-secrets "FIREBASE_PROJECT_ID=firebase-project-id:latest,\
FIREBASE_CLIENT_EMAIL=firebase-client-email:latest,\
FIREBASE_PRIVATE_KEY=firebase-private-key:latest,\
FIREBASE_PRIVATE_KEY_ID=firebase-private-key-id:latest,\
FIREBASE_CLIENT_ID=firebase-client-id:latest,\
TWILIO_ACCOUNT_SID=twilio-account-sid:latest,\
TWILIO_AUTH_TOKEN=twilio-auth-token:latest,\
TWILIO_FROM_NUMBER=twilio-from-number:latest"

echo ""
echo "Deploy completo. Verificando health check..."
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" --region "${REGION}" --format='value(status.url)')
curl -s -o /dev/null -w "HTTP %{http_code}\n" "${SERVICE_URL}/actuator/health"
echo "URL del servicio: ${SERVICE_URL}"
