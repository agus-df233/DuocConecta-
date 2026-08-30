package cl.duoc.duocconecta.usuarios.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la documentación OpenAPI que se muestra en Swagger UI.
 *
 * <p>Declara el esquema de seguridad Bearer para que desde Swagger se pueda pegar un token de
 * Azure AD y probar los endpoints protegidos con el botón "Authorize".</p>
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad. Debe coincidir con el @SecurityRequirement del controlador. */
    private static final String ESQUEMA_BEARER = "bearer-jwt";

    /**
     * Arma la definición de la API con su información general y el esquema de autenticación.
     */
    @Bean
    public OpenAPI definicionDeLaApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DuocConecta · ms-usuarios")
                        .version("v1")
                        .description("""
                                Microservicio de identidad y perfil de DuocConecta.

                                Todos los endpoints requieren un token JWT emitido por Azure AD
                                (Microsoft Entra ID). El rol se deriva del dominio del correo
                                institucional: @duocuc.cl es ESTUDIANTE, @profesor.duoc.cl es
                                PROFESOR y @duoc.cl es ACADEMICO. Cualquier otro dominio recibe 403.

                                Para probar desde acá: hacé clic en Authorize y pegá el access token
                                (sin la palabra Bearer).""")
                        .contact(new Contact().name("Equipo DuocConecta · DSY1107")))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token emitido por Azure AD para esta API.")));
    }
}
