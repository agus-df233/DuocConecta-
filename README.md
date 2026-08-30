# DuocConecta

Plataforma de **networking y vitrina de proyectos** para la comunidad de Duoc UC.
Asignatura **DSY1107 · Desarrollo Cloud Native I** — Evaluación Parcial 1.

La EP1 se centra en **autenticación con Azure AD (Entra ID)**, **validación de JWT en cada capa** y
**despliegue en la nube**. Este repositorio contiene el backend de esa entrega.

---

## Qué hay construido

| Módulo | Puerto | Qué es | Estado |
|---|---|---|---|
| `common-seguridad` | — | Librería con la validación de JWT compartida: audiencia, roles y dominios | Listo |
| `ms-usuarios` | 8081 | Microservicio de identidad y perfil. Dueño del schema `usuarios` | Listo |
| `bff-web` | 8080 | Backend for Frontend: valida el token y agrega respuestas | Listo |
| `frontend-web` | 5173 | SPA React + Vite con MSAL | Fase 2, pendiente |
| `ms-proyectos` | — | CRUD de proyectos para la vitrina | Fase 3, pendiente |

Arquitectura completa, convenciones y decisiones fijas: ver [`CLAUDE.md`](CLAUDE.md).

---

## Requisitos

- **Java 21** (el proyecto compila con `release 21`)
- **Maven 3.9+**
- **Docker** con Compose v2 (para PostgreSQL)
- Un tenant de **Azure AD (Microsoft Entra ID)** con dos registros de aplicación
  → la guía de configuración del IDaaS se mantiene fuera del repositorio; pedila al equipo

---

## Cómo levantar todo

### 1. Variables de entorno

```bash
cp .env.example .env
```

Editá `.env` y completá los valores. **`.env` nunca se sube al repositorio.**

| Variable | Obligatoria | Qué es |
|---|---|---|
| `AZURE_TENANT_ID` | **Sí** | Directory (tenant) ID de Azure AD. Arma el `issuer-uri`. |
| `AZURE_CLIENT_ID` | **Sí** | Client-id del registro de la **API**. Es la audiencia que se valida en el token. |
| `DB_URL` | No | Por defecto `jdbc:postgresql://localhost:5432/duocconecta` |
| `DB_USER` | No | Por defecto `duocconecta` |
| `DB_PASSWORD` | No | Por defecto `duocconecta` |
| `CORS_ORIGENES` | No | Por defecto `http://localhost:5173` (dev server de Vite) |
| `MS_USUARIOS_URL` | No | Por defecto `http://localhost:8081` |

`AZURE_TENANT_ID` y `AZURE_CLIENT_ID` no tienen valor por defecto a propósito: sin ellas los
servicios fallan al arrancar, en vez de levantar con una configuración de seguridad incompleta.

### 2. Base de datos

```bash
docker compose up -d

# Verificar que el schema exista
docker compose exec postgres psql -U duocconecta -d duocconecta -c "\dn"
```

Debería listar los schemas `usuarios` y `proyectos`.

> **Ojo:** `docker/postgres/init.sql` solo se ejecuta la primera vez, cuando el volumen está vacío.
> Si más adelante agregás un schema, creálo a mano o hacé `docker compose down -v` para empezar de cero
> (esto borra todos los datos).

### 3. Compilar

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
mvn clean install
```

### 4. Levantar los servicios

En dos terminales distintas, con las variables de `.env` cargadas:

```bash
set -a && source .env && set +a

mvn -pl ms-usuarios spring-boot:run     # http://localhost:8081
mvn -pl bff-web     spring-boot:run     # http://localhost:8080
```

Para levantar con **datos de ejemplo** (perfiles de varias carreras y sedes, útil para la demo):

```bash
mvn -pl ms-usuarios spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Verificación

```bash
# Salud de ambos servicios
curl -s localhost:8081/actuator/health    # {"status":"UP"}
curl -s localhost:8080/actuator/health    # {"status":"UP"}
```

### Swagger UI

- ms-usuarios: <http://localhost:8081/swagger-ui.html>
- bff-web: <http://localhost:8080/swagger-ui.html>

Cada endpoint tiene su descripción en español. Para probar los protegidos: botón **Authorize** y
pegar el access token (sin la palabra `Bearer`).

### Los tres casos de autenticación

**Sin token → 401**

```bash
curl -i localhost:8081/api/v1/usuarios/me
```

```
HTTP/1.1 401
WWW-Authenticate: Bearer
Content-Type: application/problem+json

{"type":"about:blank","title":"No autenticado","status":401,
 "detail":"La petición no incluye un token válido. Iniciá sesión con tu cuenta institucional y
 enviá el token en la cabecera Authorization: Bearer <token>.","instance":"/api/v1/usuarios/me"}
```

**Con token de dominio no permitido → 403**

```bash
curl -i -H "Authorization: Bearer $TOKEN_EXTERNO" localhost:8081/api/v1/usuarios/me
```

```
HTTP/1.1 403
Content-Type: application/problem+json

{"type":"about:blank","title":"Dominio no autorizado","status":403,
 "detail":"Tu correo no pertenece a un dominio institucional de Duoc UC.
 Entrá con tu cuenta @duocuc.cl, @profesor.duoc.cl o @duoc.cl."}
```

El perfil **no se crea**: una cuenta externa no queda registrada en la plataforma.

**Con token válido `@duocuc.cl` → 200 y auto-aprovisionamiento**

```bash
curl -i -H "Authorization: Bearer $TOKEN_DUOCUC" localhost:8081/api/v1/usuarios/me
```

```
HTTP/1.1 200
Content-Type: application/json

{"id":"3f2b...","nombre":"Juana Pérez","correo":"juana.perez@duocuc.cl",
 "rol":"ESTUDIANTE","carrera":null,"sede":null,"bio":null,
 "visible":true,"telefono":null,"redes":[]}
```

La primera llamada **crea el perfil** a partir de los claims del token y le asigna el rol según el
dominio. Las siguientes devuelven el mismo perfil.

### El resto de los endpoints

```bash
TOKEN=$TOKEN_DUOCUC

# Completar el perfil
curl -s -X PUT localhost:8081/api/v1/usuarios/me \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Juana Pérez","carrera":"Ingeniería en Informática","sede":"Plaza Oeste",
       "bio":"Backend y bases de datos.","telefono":"+56 9 1234 5678",
       "redes":["https://github.com/juanaperez"]}'

# Redes sociales del usuario autenticado
curl -s localhost:8081/api/v1/usuarios/me/redes -H "Authorization: Bearer $TOKEN"
# {"redes":["https://github.com/juanaperez"]}

# Ocultar o mostrar el perfil en las búsquedas
curl -s -X PATCH localhost:8081/api/v1/usuarios/me/visibilidad -H "Authorization: Bearer $TOKEN"

# Listado público con filtros (nunca trae teléfono ni redes)
curl -s "localhost:8081/api/v1/usuarios?carrera=Ingeniería%20en%20Informática&sede=Plaza%20Oeste" \
  -H "Authorization: Bearer $TOKEN"

# Perfil público de otra persona
curl -s localhost:8081/api/v1/usuarios/{id} -H "Authorization: Bearer $TOKEN"

# Agregación del BFF: perfil y redes en una sola llamada
curl -s localhost:8080/api/v1/bff/mi-perfil -H "Authorization: Bearer $TOKEN"
```

### Tests

```bash
mvn test
```

Cubren que el contexto de cada servicio levante, que las rutas protegidas respondan 401 sin token,
que `/actuator/health` quede abierto, que el CORS acepte solo los orígenes declarados y que el
mapeo de dominio a rol distinga `@profesor.duoc.cl` de `@duoc.cl`.

---

## Endpoints

### `ms-usuarios` — `/api/v1/usuarios`

| Método y ruta | Qué hace | Requiere |
|---|---|---|
| `GET /me` | Auto-provisiona o lee el perfil propio desde los claims del token | Rol válido |
| `PUT /me` | Actualiza nombre, carrera, sede, bio, teléfono y redes | Rol válido |
| `PATCH /me/visibilidad` | Muestra u oculta el perfil en las búsquedas | Rol válido |
| `GET /me/redes` | Devuelve las redes sociales del usuario autenticado | Rol válido |
| `GET /{id}` | Perfil público de otra persona (sin teléfono ni redes) | Token válido |
| `GET /?carrera=&sede=` | Listado de perfiles visibles, con filtros opcionales | Token válido |

### `bff-web` — `/api/v1/bff`

| Método y ruta | Qué hace |
|---|---|
| `GET /mi-perfil` | Perfil y redes del usuario autenticado, en una sola respuesta |

**"Público" quiere decir sin datos de contacto, no sin autenticación.** Todos los endpoints exigen
token; solo `/actuator/health`, `/swagger-ui/**` y `/v3/api-docs/**` quedan abiertos.

`telefono` y `redes` **nunca** aparecen en respuestas públicas. `/me/redes` devuelve solo las del
propio usuario; compartirlas con terceros queda sujeto al consentimiento mutuo, que se implementa
en EP2.

---

## Seguridad

- `ms-usuarios` y `bff-web` son **OAuth2 Resource Server** y validan el JWT por separado:
  firma, vigencia, emisor y **audiencia** (esta última con un `OAuth2TokenValidator` propio).
  Es defensa en profundidad: el token se valida en el BFF, en el API Manager y en el microservicio.
- El rol se toma de los **App Roles** de Azure AD si el tenant los emite; si no, se **deriva del
  dominio del correo**:

  | Dominio | Rol |
  |---|---|
  | `@duocuc.cl` | `ESTUDIANTE` |
  | `@profesor.duoc.cl` | `PROFESOR` |
  | `@duoc.cl` | `ACADEMICO` |
  | cualquier otro | **403**, no se crea perfil |

  El mapa vive en `application.yml` (`duocconecta.seguridad.dominios`): sumar un dominio es agregar
  una línea, sin tocar código.
- El `oid` y el correo se leen **siempre del token**, nunca de un parámetro de la petición.
- **CORS explícito** en el BFF: orígenes, métodos y cabeceras declarados uno por uno, sin comodines.
- **Sin secretos en el repositorio**: todo por variable de entorno. `.env` está en `.gitignore`.

La guía paso a paso de Azure AD (registros de app, claims, MSAL y troubleshooting) se mantiene
fuera del repositorio junto con el resto de la documentación del proyecto.

---

## Estructura del repositorio

```
DuocConecta/
├── CLAUDE.md                  Contexto y reglas del proyecto
├── README.md                  Este archivo
├── .env.example               Plantilla de variables de entorno
├── pom.xml                    POM padre: Java 21, Spring Boot 4.1.1
├── docker-compose.yml         PostgreSQL 16.15
├── docker/Dockerfile          Imagen de cualquiera de los servicios (ARG SERVICIO)
├── docker/postgres/init.sql   Creación de los schemas
├── infra/aws.sh               Despliegue en AWS: verificar, crear, build, desplegar, apagar
├── infra/task-definition.json Los tres servicios en una tarea de Fargate
├── Makefile                   Atajos: make crear / desplegar / apagar / local / test
├── common-seguridad/          Validación de JWT compartida
├── ms-usuarios/               Microservicio de perfiles
├── bff-web/                   Backend for Frontend
└── frontend-web/              Reservado para la Fase 2
```

Cada microservicio organiza su paquete en seis capas, en este orden:
`domain` → `repository` → `service` → `controller` → `dto` → `config`.

---

## Nota sobre la versión de Spring Boot

El proyecto usa **Spring Boot 4.1.1**, no 3.x. La razón está documentada en
[`CLAUDE.md` §3.1](CLAUDE.md): a agosto de 2026 toda la rama 3.x está fuera de soporte open source
(3.5 terminó el 30-jun-2026) y arrastra CVEs de Spring Security sin parche OSS disponible. Se
priorizó usar una línea mantenida y sin vulnerabilidades conocidas, con
`spring-security.version` forzado a **7.1.1**.
