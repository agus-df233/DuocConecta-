# Puerta de entrada única a las tareas del proyecto.
# Envuelve infra/aws.sh para que nadie tenga que recordar los subcomandos.
#
#   make verificar                    Qué habilita el lab de AWS. Correr primero.
#   make crear                        Crea la infraestructura (ECR, ECS, RDS, ALB, API Gateway).
#   make desplegar                    Construye las imágenes, las sube y actualiza el servicio.
#   make desplegar SERVICIO=bff-web   Igual, pero sube solo ese servicio.
#   make iniciar / make apagar        Prende y apaga las tareas. APAGAR al terminar la jornada.
#   make local                        Levanta Postgres y compila todo en la máquina.
#   make test                         Corre la suite completa.

.PHONY: verificar crear desplegar iniciar apagar urls local test front limpiar

# Si no se indica SERVICIO, se construyen los que existan en el repo.
SERVICIO ?=

verificar:
	./infra/aws.sh verificar

crear:
	./infra/aws.sh crear

desplegar:
ifeq ($(SERVICIO),)
	./infra/aws.sh build ms-usuarios
	./infra/aws.sh build bff-web
	@test -d ms-proyectos && ./infra/aws.sh build ms-proyectos || echo "  ! ms-proyectos aún no existe, se omite"
else
	./infra/aws.sh build $(SERVICIO)
endif
	./infra/aws.sh desplegar

iniciar:
	./infra/aws.sh iniciar

apagar:
	./infra/aws.sh apagar

urls:
	./infra/aws.sh urls

# --- Desarrollo local ---
local:
	docker compose up -d
	mvn -q clean install -DskipTests
	@echo "Listo. Levantá los servicios con:"
	@echo "  mvn -pl ms-usuarios spring-boot:run -Dspring-boot.run.profiles=dev"
	@echo "  mvn -pl bff-web     spring-boot:run"

test:
	mvn test

front:
	cd frontend-web && npm install && npm run dev

limpiar:
	mvn -q clean
	docker compose down
