# syntax=docker/dockerfile:1
#
# Build multi-stage para nexabank-api (Spring Boot 3.2.5 / Java 17, reactor Maven con
# padre en NexaBank/pom.xml). Build context: raíz del repo (NexaBank/), no nexabank-api/,
# porque Maven necesita el pom.xml padre.
#
# Deliberadamente NO copia firebase-service-account.json (excluido vía .dockerignore) —
# en producción (Cloud Run) las credenciales de Firebase viajan por las variables de
# entorno individuales que FirebaseConfig.java ya soporta, inyectadas desde Secret
# Manager. Ver docs/FRONTEND_BACKEND_INTEGRATION_PLAN.md Fase B.

# ---- Etapa de build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cachear dependencias en su propia capa antes de copiar el código fuente
COPY pom.xml ./pom.xml
COPY nexabank-api/pom.xml ./nexabank-api/pom.xml
RUN mvn -q -pl nexabank-api -am dependency:go-offline

COPY nexabank-api/src ./nexabank-api/src
RUN mvn -q -pl nexabank-api -am package -DskipTests

# ---- Etapa de runtime ----
# eclipse-temurin:17-jre-alpine (musl libc) causa SIGSEGV nativo al cargar la librería
# TCNative/BoringSSL de Netty que trae gRPC (usado por el SDK de Firebase Admin para
# Firestore) — esa librería .so se publica compilada para glibc, no musl. Se usa la
# variante Debian/glibc del mismo tag para evitar el crash (ver logs de Render del
# primer deploy: SIGSEGV en netty_internal_tcnative_SSLContext_JNI_OnLoad).
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd -r nexabank && useradd -r -g nexabank nexabank
COPY --from=build /workspace/nexabank-api/target/*.jar app.jar
USER nexabank

# Cloud Run inyecta $PORT en runtime; 8080 es el default local/documentado en
# application.properties (server.port=${SERVER_PORT:8080}).
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${PORT:-8080}"]
