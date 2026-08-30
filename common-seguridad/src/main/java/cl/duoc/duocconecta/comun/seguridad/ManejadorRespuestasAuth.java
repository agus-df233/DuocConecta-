package cl.duoc.duocconecta.comun.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuestas de error de autenticación y autorización, con mensajes claros en español.
 *
 * <p>Sin esto, Spring devuelve un 401 y un 403 con el cuerpo vacío y quien integra el frontend no
 * tiene forma de saber si el problema fue el token, la audiencia o el rol.</p>
 *
 * <p>El JSON se arma a mano en vez de usar un serializador para no acoplar este módulo compartido
 * a una versión concreta de Jackson. Los textos son constantes del código, así que no hay riesgo
 * de romper el formato.</p>
 */
@Configuration
public class ManejadorRespuestasAuth {

    /**
     * Respuesta cuando la petición llega sin token o con un token inválido.
     *
     * <p>Devuelve 401 e incluye la cabecera {@code WWW-Authenticate}, como pide el estándar de
     * Bearer tokens.</p>
     */
    @Bean
    public AuthenticationEntryPoint puntoDeEntradaNoAutenticado() {
        return (peticion, respuesta, excepcion) -> escribirProblema(
                peticion,
                respuesta,
                HttpStatus.UNAUTHORIZED,
                "No autenticado",
                "La petición no incluye un token válido. Iniciá sesión con tu cuenta institucional "
                        + "y enviá el token en la cabecera Authorization: Bearer <token>.");
    }

    /**
     * Respuesta cuando el token es válido pero el usuario no tiene permiso.
     *
     * <p>Cubre dos casos: el rol no alcanza para el endpoint, o el correo pertenece a un dominio
     * que no está autorizado en la plataforma.</p>
     */
    @Bean
    public AccessDeniedHandler manejadorAccesoDenegado() {
        return (peticion, respuesta, excepcion) -> escribirProblema(
                peticion,
                respuesta,
                HttpStatus.FORBIDDEN,
                "Acceso denegado",
                "Tu cuenta no tiene permiso para esta operación. Puede que tu correo no pertenezca a "
                        + "un dominio institucional autorizado de Duoc UC, o que tu rol no alcance "
                        + "para este recurso.");
    }

    /**
     * Escribe el cuerpo del error con el formato de Problem Details (RFC 9457).
     */
    private void escribirProblema(HttpServletRequest peticion,
                                  HttpServletResponse respuesta,
                                  HttpStatus estado,
                                  String titulo,
                                  String detalle) throws IOException {

        if (estado == HttpStatus.UNAUTHORIZED) {
            respuesta.setHeader("WWW-Authenticate", "Bearer");
        }
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String cuerpo = """
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","instance":"%s"}"""
                .formatted(titulo, estado.value(), detalle, peticion.getRequestURI());

        respuesta.getWriter().write(cuerpo);
    }
}
