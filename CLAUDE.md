# CLAUDE.md — Contexto y reglas del proyecto DuocConecta

Este archivo es el contexto permanente para cualquier sesión del agente sobre este repositorio.
Leelo antes de escribir código. Si una decisión cambia, **actualizá este archivo**.

---

## 1. Qué es DuocConecta

Plataforma de **networking y vitrina de proyectos** para la comunidad de Duoc UC.
Asignatura **DSY1107 · Desarrollo Cloud Native I**.

El problema que resuelve: hoy el conocimiento que genera la comunidad (proyectos, prompts, recursos)
vive disperso en grupos de WhatsApp y Drives personales. Cada semestre se pierde, no cruza entre
carreras ni entre sedes, y quien llega nuevo empieza de cero. DuocConecta centraliza ese intercambio
en una plataforma institucional, con un mecanismo de contacto que **respeta el consentimiento**:
nadie ve los datos de contacto de otro hasta que ambos aceptan colaborar.

### Objetivo de la EP1

La EP1 evalúa tres cosas: **autenticación**, **validación de JWT** y **despliegue en la nube**.
No evalúa la funcionalidad completa de la plataforma. Todo lo que se construya debe servir a esos
tres puntos.

---

## 2. Arquitectura

```
React (SPA)  →  BFF  →  API Manager (AWS API Gateway)  →  microservicios  →  PostgreSQL / S3
                 ↑                    ↑                          ↑
                 └────────── validación de JWT en cada capa ──────┘
                          (defensa en profundidad)
```

**IDaaS: Azure AD (Microsoft Entra ID)** con flujo **OIDC Authorization Code + PKCE**.
La aplicación nunca almacena contraseñas.

- El **frontend** nunca llama a los microservicios directamente: solo conoce al BFF.
- El **BFF** valida el token, agrega respuestas de varios microservicios en una sola llamada y
  aplica circuit breaker.
- El **API Manager** vuelve a validar el token y enruta al microservicio correcto.
- Los **microservicios** ejecutan la lógica de negocio y validan el token otra vez.

---

## 3. Decisiones fijas (respetalas)

### Stack

| Qué | Decisión |
|---|---|
| Lenguaje | **Java 21** |
| Framework | **Spring Boot 4.1.1** (ver §3.1) |
| Spring Security | **7.1.1** (forzado por `<spring-security.version>`) |
| Build | **Maven**, monorepo multi-módulo |
| Base de datos | **PostgreSQL 16.15**, 1 contenedor, **un schema por servicio** |
| Migraciones | **Flyway** |
| Documentación API | **springdoc-openapi 3.1.0** (Swagger UI) |
| Adjuntos | **Amazon S3** (en la base solo la referencia; no aplica a `ms-usuarios` en v1) |
| Prefijo de API | **`/api/v1`** |

### 3.1 Por qué Spring Boot 4.1.1 y no 3.x

La decisión original del enunciado decía "Spring Boot 3.x" y a la vez "última patch de una línea
mantenida y sin CVEs abiertos". **En agosto de 2026 esas dos condiciones son incompatibles**:

- Toda la rama 3.x está EOL en open source. La línea 3.5 terminó su soporte OSS el **30-jun-2026**
  (última patch: 3.5.16); 3.4 terminó en dic-2025.
- Spring Security 6.5.x arrastra CVE-2026-47841 y CVE-2026-47842 cuyos fixes OSS **no existen**
  (6.5.12 es solo Enterprise Support).

Se priorizó "mantenida y sin CVEs". Se usa **Boot 4.1.1** (soporte OSS hasta jul-2027) con
`spring-security.version` forzado a **7.1.1**, que corrige los tres CVE publicados el 20-ago-2026:

| CVE | Qué es | Fix OSS |
|---|---|---|
| CVE-2026-41707 | Replay de proof DPoP por desalojo de caché | 7.1.1 |
| CVE-2026-47841 | Bypass de user verification en WebAuthn | 7.1.1 |
| CVE-2026-47842 | `AesBytesEncryptor` determinista con IV nulo | 7.1.1 |

### 3.2 Consecuencias de Spring Boot 4 (¡importante al escribir código!)

Boot 4 renombró artefactos y subió a Jackson 3. No copies recetas de Boot 3:

| Boot 3 (NO usar) | Boot 4 (usar) |
|---|---|
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** |
| `spring-boot-starter-oauth2-resource-server` | **`spring-boot-starter-security-oauth2-resource-server`** |
| Flyway transitivo | **`spring-boot-starter-flyway` explícito** |
| `com.fasterxml.jackson.*` | Jackson 3: **`tools.jackson.*`** |
| `@MockBean` / `@SpyBean` | **`@MockitoBean` / `@MockitoSpyBean`** |
| `AntPathRequestMatcher`, `.and()` | **solo lambda DSL**; `PathPatternRequestMatcher` con patrones absolutos |

Los DTOs son **records**, así no hace falta tocar Jackson directamente.

### Roles y dominios de correo

El rol se determina por el **dominio del correo institucional** que viene en el token:

| Dominio | Rol |
|---|---|
| `@duocuc.cl` | `ESTUDIANTE` |
| `@profesor.duoc.cl` | `PROFESOR` |
| `@duoc.cl` | `ACADEMICO` |
| cualquier otro | **rechazar** → 403, no se auto-provisiona el perfil |

La lógica vive **centralizada y configurable** en `ResolvedorRol` + `PropiedadesSeguridad`
(`duocconecta.seguridad.dominios` en `application.yml`), porque pueden sumarse dominios.

**Regla crítica:** el match del dominio es **exacto sobre el texto después del último `@`**, nunca
`endsWith`. Con `endsWith`, `alguien@profesor.duoc.cl` haría match también con `duoc.cl` y quedaría
clasificado mal según el orden de iteración del mapa.

### Fuera de alcance de la EP1

**No generar**: `ms-prompts`, RabbitMQ, Kafka, moderación, notificaciones, S3.
Se difieren a EP2/EP3.

---

## 4. Alcance por fases

| Fase | Qué | Estado |
|---|---|---|
| **1** | Monorepo, `common-seguridad`, `ms-usuarios`, `bff-web`, PostgreSQL, Swagger, tests | Construida |
| **2** | `frontend-web` React + Vite con MSAL (Authorization Code + PKCE) | Pendiente |
| **3** | `ms-proyectos` (CRUD) + agregación en el BFF → llena la vitrina | Pendiente |

El objetivo de la demo es: **login institucional → IDaaS → vitrina de proyectos con el perfil del
usuario relleno**. El frontend SÍ es parte del alcance, solo va después del backend.

Por eso `frontend-web/` ya está reservado en el árbol y el CORS del BFF ya acepta
`http://localhost:5173`. El front **no es módulo Maven**: se construye con npm/Vite aparte.

---

## 5. Convenciones de código (obligatorias)

### Idioma

**Todo en español latino**: comentarios, mensajes de error, commits, documentación y descripciones de
OpenAPI. Los nombres de clases y variables pueden ir en español o inglés técnico.

Los comentarios explican **el qué y el por qué**, en lenguaje simple, sin tecnicismos innecesarios.
Debe haber un comentario breve arriba de:
- cada clase,
- cada método público,
- cada bloque de lógica no obvia.

### Orden de capas

Cada microservicio organiza su paquete en estas 6 capas, **en este orden**:

1. **`domain/`** — entidades JPA y enums del dominio
2. **`repository/`** — interfaces Spring Data JPA
3. **`service/`** — lógica de negocio
4. **`controller/`** — controladores REST (`@RestController`)
5. **`dto/`** — objetos de entrada/salida de la API
6. **`config/`** — seguridad, CORS, OpenAPI y demás configuración

### Reglas duras

- **Nunca exponer entidades JPA** en la API. Siempre DTOs (records).
- **`telefono` y `redes` nunca salen en respuestas públicas.** Son datos de contacto privados.
  `GET /usuarios/{id}` y el listado devuelven `PerfilPublicoResponse`, que no los tiene.
  `/me/redes` devuelve solo las del propio usuario autenticado. Compartirlas con terceros queda
  sujeto al consentimiento mutuo en EP2.
- El `oid` y el correo se extraen **siempre del `Jwt`**, nunca por parámetro de la petición.
  Usá el componente `UsuarioActual`.
- Validación de entrada con **`jakarta.validation`**.
- Cada endpoint documentado con **Javadoc en español** *y* **`@Operation(summary = "...")`**.

### Seguridad

- Cada microservicio y el BFF son **OAuth2 Resource Server** y validan el JWT: firma, vigencia,
  issuer y **audience** (esta última con un `OAuth2TokenValidator` propio, `ValidadorAudiencia`).
- Autorización por rol con `@PreAuthorize`.
- Códigos de respuesta: sin token → **401**; rol insuficiente o dominio no permitido → **403**;
  válido → **200**.
- **CORS explícito**: orígenes y métodos declarados, **sin comodines**.

---

## 6. Restricciones

- **Nunca subir secretos.** `issuer-uri`, `client-id`, `tenant-id`, credenciales de base de datos:
  todo por variable de entorno / `application.yml` con `${VAR:default-dev}`. Nunca hardcodear.
  `.env` está en `.gitignore`; usá `.env.example` como plantilla.
- No generar `ms-prompts`, RabbitMQ, Kafka, moderación ni notificaciones en EP1.
- No exponer entidades JPA directamente.
- `telefono` / `redes` nunca en respuestas públicas.

---

## 7. Estructura de módulos

```
DuocConecta/
├── pom.xml                 # parent (packaging pom), versiones centralizadas
├── docker-compose.yml      # 1 contenedor postgres:16.15-alpine
├── docker/postgres/init.sql
├── docs/azure-entra-id.md  # guía de configuración del IDaaS
├── common-seguridad/       # validación de JWT compartida (audiencia, roles, dominios)
├── ms-usuarios/            # puerto 8081, schema `usuarios`
├── bff-web/                # puerto 8080
└── frontend-web/           # Fase 2 — React + Vite (no es módulo Maven)
```

**Sobre `common-seguridad`:** el validador de audiencia, el conversor de roles y el mapa dominio→rol
son idénticos en `ms-usuarios` y `bff-web`, y los microservicios que vienen (`ms-proyectos`,
`ms-contacto`) los van a necesitar igual. Duplicarlos garantizaría que se desincronicen.

---

## 8. Cómo levantar el proyecto

```bash
# Java 21 (el proyecto compila con release 21)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 1. Base de datos
docker compose up -d

# 2. Compilar todo
mvn clean install

# 3. Levantar los servicios (dos terminales)
mvn -pl ms-usuarios spring-boot:run     # http://localhost:8081
mvn -pl bff-web     spring-boot:run     # http://localhost:8080
```

Swagger UI: `http://localhost:8081/swagger-ui.html` y `http://localhost:8080/swagger-ui.html`.
Health: `http://localhost:8081/actuator/health` y `http://localhost:8080/actuator/health`.

Ver `README.md` para las variables de entorno y `docs/azure-entra-id.md` para configurar el IDaaS.

---

## 9. Endpoints de `ms-usuarios`

| Método y ruta | Qué hace | Auth |
|---|---|---|
| `GET /api/v1/usuarios/me` | Auto-provisiona/lee el perfil del usuario autenticado desde los claims | rol válido |
| `PUT /api/v1/usuarios/me` | Actualiza el perfil propio | rol válido |
| `PATCH /api/v1/usuarios/me/visibilidad` | Alterna `visible` | rol válido |
| `GET /api/v1/usuarios/me/redes` | Devuelve las redes sociales del usuario autenticado | rol válido |
| `GET /api/v1/usuarios/{id}` | Perfil público (sin telefono/redes; respeta `visible`) | autenticado |
| `GET /api/v1/usuarios?carrera=&sede=` | Listado público de perfiles visibles, con filtros | autenticado |

"Público" significa **sin datos de contacto**, no sin autenticación: la propuesta de arquitectura
pide validación de JWT en cada capa. Solo `/actuator/health`, `/swagger-ui/**` y `/v3/api-docs/**`
quedan abiertos.

---

## 10. Cómo sumar un microservicio nuevo

Receta para que un módulo nuevo encaje sin retrabajo. `ms-proyectos` es el primer caso.

| Qué | Valor |
|---|---|
| Puerto | `ms-proyectos` → **8082**; el siguiente, 8083 |
| Schema propio | **`proyectos`** (ya creado en `docker/postgres/init.sql`) |
| Paquete raíz | `cl.duoc.duocconecta.proyectos` |
| Prefijo de rutas | `/api/v1/proyectos` |

**Pasos:**

1. Declarar el módulo en `<modules>` del `pom.xml` raíz.
2. Copiar las dependencias de `ms-usuarios/pom.xml`, **incluida `common-seguridad`**.
3. Copiar `config/SeguridadConfig.java` y `config/OpenApiConfig.java` tal cual: solo cambia el
   paquete. Eso ya deja el servicio validando el JWT igual que los demás.
4. Respetar el orden de capas: `domain` → `repository` → `service` → `controller` → `dto` → `config`.
5. `application.yml`: puerto propio, `spring.flyway.schemas` y `default-schema` con el schema
   propio, y **`create-schemas: true`** (en RDS no se ejecuta `init.sql`).
6. Migración `V1__proyectos.sql` en `src/main/resources/db/migration/`.
7. Tests: copiar el patrón de `MsUsuariosApplicationTests` — contexto que levanta y ruta protegida
   que responde 401. En `application-test.yml` usar `jwk-set-uri` y **nunca `issuer-uri`**
   (este último hace descubrimiento OIDC contra la red y el test falla sin internet), y tapar el
   `issuer-uri` heredado con `issuer-uri: ""`.

**Nunca hace falta tocar** `common-seguridad`: la validación de audiencia, el mapeo de roles y
`UsuarioActual` ya sirven a cualquier microservicio.

El despliegue ya está preparado: el repositorio de ECR, el contenedor en
`infra/task-definition.json` y el target group del ALB existen de antemano. Cuando el módulo esté
en el repo, se despliega con `make desplegar SERVICIO=ms-proyectos`.

---

## 11. Infraestructura y despliegue

```
localhost:5173     API Gateway HTTP API      ALB          ECS Fargate — 1 tarea, 3 contenedores
   (front)     →   (validación de JWT)   →  (rutas)  →    ├── bff-web      :8080
                                                          ├── ms-usuarios  :8081  → RDS PostgreSQL
                                                          └── ms-proyectos :8082
```

Corre en **AWS Academy Learner Lab**, lo que impone tres cosas:

- **No se pueden crear roles IAM.** Las tareas usan `LabRole` como `executionRoleArn` y
  `taskRoleArn`. El `ecsTaskExecutionRole` de los tutoriales no existe ahí.
- **Cloud Map no está disponible**, así que los tres servicios comparten una sola tarea de Fargate
  y se comunican por `localhost`. Se pierde el escalado independiente.
- **CloudFront está bloqueado.** Como Azure AD exige HTTPS en los URI de redirección (salvo
  `localhost`), el frontend corre desde `http://localhost:5173` contra el backend desplegado.

La contraseña de RDS la gestiona **Secrets Manager**: la base se crea con
`--manage-master-user-password` y la task definition la inyecta por ARN. Nunca existe en el repo.

```bash
make verificar    # qué habilita el lab. Correr primero.
make crear        # ECR, cluster, RDS, ALB, API Gateway. Idempotente.
make desplegar    # construye, sube a ECR y actualiza el servicio
make apagar       # baja las tareas a cero — CORRER AL TERMINAR LA JORNADA
```

El crédito del lab es de **$50 y si se agota se pierde todo el entorno**. El ALB y RDS no se apagan
solos: `make apagar` solo baja las tareas de Fargate.
