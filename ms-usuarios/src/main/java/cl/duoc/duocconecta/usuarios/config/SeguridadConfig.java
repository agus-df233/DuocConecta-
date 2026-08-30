package cl.duoc.duocconecta.usuarios.config;

import cl.duoc.duocconecta.comun.seguridad.ConversorRolesJwt;
import cl.duoc.duocconecta.comun.seguridad.ManejadorRespuestasAuth;
import cl.duoc.duocconecta.comun.seguridad.SeguridadBaseConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Configuración de seguridad de ms-usuarios.
 *
 * <p>El microservicio es un OAuth2 Resource Server: valida el JWT de Azure AD por su cuenta, aunque
 * el BFF y el API Manager ya lo hayan validado antes. Es defensa en profundidad: si alguien logra
 * llegar al microservicio salteándose las capas de adelante, igual necesita un token válido.</p>
 */
@Configuration
@EnableMethodSecurity
@Import({SeguridadBaseConfig.class, ManejadorRespuestasAuth.class})
public class SeguridadConfig {

    /** Rutas abiertas: monitoreo y documentación. No exponen datos de nadie. */
    private static final String[] RUTAS_PUBLICAS = {
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    /**
     * Arma la cadena de filtros de seguridad.
     *
     * <p>Todo lo que está bajo {@code /api/v1} exige token. Los endpoints "públicos" de la API lo
     * son en el sentido de que no exponen datos de contacto, no en el de que se puedan consultar
     * sin autenticarse.</p>
     */
    @Bean
    public SecurityFilterChain cadenaDeSeguridad(
            HttpSecurity http,
            ConversorRolesJwt conversorRolesJwt,
            AuthenticationEntryPoint puntoDeEntradaNoAutenticado,
            AccessDeniedHandler manejadorAccesoDenegado) throws Exception {

        http
            // La API no usa cookies ni sesión: la identidad viaja en el token de cada petición,
            // así que no hay nada que proteger con CSRF.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // El CORS lo maneja el BFF, que es el único componente que habla con el navegador.
            .cors(cors -> cors.disable())

            .authorizeHttpRequests(rutas -> rutas
                    .requestMatchers(RUTAS_PUBLICAS).permitAll()
                    .anyRequest().authenticated())

            // Valida firma, vigencia, emisor y audiencia, y traduce el rol a una authority.
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorRolesJwt))
                    .authenticationEntryPoint(puntoDeEntradaNoAutenticado)
                    .accessDeniedHandler(manejadorAccesoDenegado))

            // Mismos manejadores para el resto de la cadena, así los 401 y 403 salen siempre
            // con el mismo formato.
            .exceptionHandling(errores -> errores
                    .authenticationEntryPoint(puntoDeEntradaNoAutenticado)
                    .accessDeniedHandler(manejadorAccesoDenegado));

        return http.build();
    }
}
