package cl.duoc.duocconecta.bff.config;

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
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Configuración de seguridad del BFF.
 *
 * <p>Valida el JWT exactamente igual que los microservicios: mismo emisor, misma audiencia, mismo
 * mapeo de roles. Es la primera de las tres capas que revisan el token (BFF, API Manager y
 * microservicio), y la que rechaza antes las peticiones sin credenciales.</p>
 */
@Configuration
@EnableMethodSecurity
@Import({SeguridadBaseConfig.class, ManejadorRespuestasAuth.class})
public class SeguridadConfig {

    /** Rutas abiertas: monitoreo y documentación. */
    private static final String[] RUTAS_PUBLICAS = {
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    /**
     * Arma la cadena de filtros de seguridad del BFF.
     *
     * <p>A diferencia del microservicio, acá sí se habilita CORS: el BFF es el único componente
     * que recibe peticiones directamente del navegador.</p>
     */
    @Bean
    public SecurityFilterChain cadenaDeSeguridad(
            HttpSecurity http,
            ConversorRolesJwt conversorRolesJwt,
            CorsConfigurationSource fuenteDeConfiguracionCors,
            AuthenticationEntryPoint puntoDeEntradaNoAutenticado,
            AccessDeniedHandler manejadorAccesoDenegado) throws Exception {

        http
            // Sin cookies ni sesión: la identidad viaja en el token de cada petición.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .cors(cors -> cors.configurationSource(fuenteDeConfiguracionCors))

            .authorizeHttpRequests(rutas -> rutas
                    .requestMatchers(RUTAS_PUBLICAS).permitAll()
                    .anyRequest().authenticated())

            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorRolesJwt))
                    .authenticationEntryPoint(puntoDeEntradaNoAutenticado)
                    .accessDeniedHandler(manejadorAccesoDenegado))

            .exceptionHandling(errores -> errores
                    .authenticationEntryPoint(puntoDeEntradaNoAutenticado)
                    .accessDeniedHandler(manejadorAccesoDenegado));

        return http.build();
    }
}
