package cl.duoc.duocconecta.bff.controller;

import cl.duoc.duocconecta.comun.seguridad.CorreoNoPresenteException;
import cl.duoc.duocconecta.comun.seguridad.DominioNoPermitidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Traduce a respuestas HTTP los errores del BFF, incluidos los que vienen de los microservicios.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /** El correo del token no pertenece a un dominio institucional autorizado. */
    @ExceptionHandler(DominioNoPermitidoException.class)
    public ProblemDetail manejarDominioNoPermitido(DominioNoPermitidoException excepcion) {
        log.warn("Se rechazó un acceso desde el dominio no autorizado '{}'.", excepcion.getDominio());

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problema.setTitle("Dominio no autorizado");
        problema.setDetail("Tu correo no pertenece a un dominio institucional de Duoc UC. "
                + "Entrá con tu cuenta @duocuc.cl, @profesor.duoc.cl o @duoc.cl.");
        return problema;
    }

    /** El token es válido pero no trae el correo del usuario. */
    @ExceptionHandler(CorreoNoPresenteException.class)
    public ProblemDetail manejarCorreoAusente(CorreoNoPresenteException excepcion) {
        log.error("Token sin claim de correo: {}", excepcion.getMessage());

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problema.setTitle("No se pudo determinar tu correo institucional");
        problema.setDetail("El token no incluye tu correo, así que no se puede asignar un rol. "
                + "Avisá al equipo: falta configurar los claims opcionales del access token en Azure AD.");
        return problema;
    }

    /**
     * Un microservicio respondió con un error.
     *
     * <p>Los errores del propio usuario (4xx) se devuelven tal cual, porque son suyos y necesita
     * verlos. Los del servidor (5xx) se convierten en 502: el problema es del backend, no de quien
     * hizo la petición.</p>
     */
    @ExceptionHandler(HttpStatusCodeException.class)
    public ProblemDetail manejarErrorDeMicroservicio(HttpStatusCodeException excepcion) {
        HttpStatus estadoRecibido = HttpStatus.valueOf(excepcion.getStatusCode().value());

        if (estadoRecibido.is4xxClientError()) {
            ProblemDetail problema = ProblemDetail.forStatus(estadoRecibido);
            problema.setTitle("La petición fue rechazada");
            problema.setDetail("ms-usuarios rechazó la petición con el estado "
                    + estadoRecibido.value() + ".");
            return problema;
        }

        log.error("ms-usuarios respondió {} al BFF.", estadoRecibido.value(), excepcion);
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        problema.setTitle("Error en un servicio interno");
        problema.setDetail("No se pudo completar la operación porque un servicio interno falló. "
                + "Intentá de nuevo en unos momentos.");
        return problema;
    }

    /**
     * No se pudo contactar al microservicio: está caído, o se agotó el tiempo de espera.
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail manejarMicroservicioInalcanzable(ResourceAccessException excepcion) {
        log.error("No se pudo contactar a ms-usuarios desde el BFF.", excepcion);

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problema.setTitle("Servicio no disponible");
        problema.setDetail("El servicio de usuarios no está respondiendo. "
                + "Verificá que ms-usuarios esté levantado e intentá de nuevo.");
        return problema;
    }
}
