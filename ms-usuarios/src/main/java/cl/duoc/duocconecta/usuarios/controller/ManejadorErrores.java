package cl.duoc.duocconecta.usuarios.controller;

import cl.duoc.duocconecta.comun.seguridad.CorreoNoPresenteException;
import cl.duoc.duocconecta.comun.seguridad.DominioNoPermitidoException;
import cl.duoc.duocconecta.usuarios.service.UsuarioNoEncontradoException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones del microservicio a respuestas HTTP con mensajes claros en español.
 *
 * <p>Usa el formato Problem Details (RFC 9457), que es el que Spring devuelve por defecto para
 * los errores, así el frontend recibe siempre la misma forma.</p>
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /**
     * El correo del token no pertenece a un dominio institucional autorizado.
     *
     * <p>Es el caso de una cuenta externa: se responde 403 y no se crea ningún perfil.</p>
     */
    @ExceptionHandler(DominioNoPermitidoException.class)
    public ProblemDetail manejarDominioNoPermitido(DominioNoPermitidoException excepcion) {
        log.warn("Se rechazó un acceso desde el dominio no autorizado '{}'.", excepcion.getDominio());

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problema.setTitle("Dominio no autorizado");
        problema.setDetail("Tu correo no pertenece a un dominio institucional de Duoc UC. "
                + "Entrá con tu cuenta @duocuc.cl, @profesor.duoc.cl o @duoc.cl.");
        return problema;
    }

    /**
     * El token es válido pero no trae el correo del usuario.
     *
     * <p>Casi siempre es un problema de configuración del tenant, no del usuario, así que el
     * mensaje apunta a eso.</p>
     */
    @ExceptionHandler(CorreoNoPresenteException.class)
    public ProblemDetail manejarCorreoAusente(CorreoNoPresenteException excepcion) {
        log.error("Token sin claim de correo: {}", excepcion.getMessage());

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problema.setTitle("No se pudo determinar tu correo institucional");
        problema.setDetail("El token no incluye tu correo, así que no se puede asignar un rol. "
                + "Avisá al equipo: falta configurar los claims opcionales del access token en Azure AD.");
        return problema;
    }

    /** El perfil pedido no existe o su dueño lo ocultó. */
    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ProblemDetail manejarUsuarioNoEncontrado(UsuarioNoEncontradoException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problema.setTitle("Perfil no encontrado");
        problema.setDetail("No se encontró un perfil visible con ese identificador.");
        return problema;
    }

    /**
     * Los datos enviados no pasaron las validaciones de {@code jakarta.validation}.
     *
     * <p>Se devuelve el detalle campo por campo para que el formulario del frontend pueda mostrar
     * cada error donde corresponde.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException excepcion) {
        Map<String, String> erroresPorCampo = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                erroresPorCampo.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setTitle("Datos inválidos");
        problema.setDetail("Revisá los campos marcados y volvé a intentar.");
        problema.setProperty("errores", erroresPorCampo);
        return problema;
    }
}
