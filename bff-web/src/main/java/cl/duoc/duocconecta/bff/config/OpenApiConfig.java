package cl.duoc.duocconecta.bff.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI del BFF, visible en Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad. Debe coincidir con el @SecurityRequirement del controlador. */
    private static final String ESQUEMA_BEARER = "bearer-jwt";

    /**
     * Arma la definición de la API del BFF con su esquema de autenticación.
     */
    @Bean
    public OpenAPI definicionDeLaApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DuocConecta · bff-web")
                        .version("v1")
                        .description("""
                                Backend for Frontend de DuocConecta: la única puerta de entrada del
                                frontend React.

                                Valida el token de Azure AD igual que los microservicios y agrega en
                                una sola respuesta los datos que cada pantalla necesita, propagando
                                el token del usuario hacia atrás.

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
