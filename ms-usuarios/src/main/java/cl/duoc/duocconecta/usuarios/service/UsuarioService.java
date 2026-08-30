package cl.duoc.duocconecta.usuarios.service;

import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.usuarios.domain.Usuario;
import cl.duoc.duocconecta.usuarios.dto.ActualizarPerfilRequest;
import cl.duoc.duocconecta.usuarios.dto.PerfilPrivadoResponse;
import cl.duoc.duocconecta.usuarios.dto.PerfilPublicoResponse;
import cl.duoc.duocconecta.usuarios.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lógica de negocio de los perfiles de usuario.
 *
 * <p>La identidad siempre se toma del token a través de {@link UsuarioActual}; ningún método
 * recibe el {@code oid} o el correo por parámetro, para que nadie pueda operar sobre el perfil
 * ajeno cambiando un valor de la petición.</p>
 *
 * <p>Los métodos públicos devuelven DTOs y no entidades. Es a propósito: la conversión tiene que
 * ocurrir <strong>dentro</strong> de la transacción, porque la lista de redes se carga de forma
 * perezosa y fuera de la transacción ya no hay sesión con la base para ir a buscarla.</p>
 *
 * <p>Las transacciones se manejan con {@link TransactionTemplate} en vez de con anotaciones porque
 * el auto-aprovisionamiento necesita reintentar en una transacción <em>nueva</em>, y una anotación
 * no permite eso cuando el método se llama a sí mismo.</p>
 */
@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository repositorio;
    private final UsuarioActual usuarioActual;
    private final TransactionTemplate transaccion;

    public UsuarioService(UsuarioRepository repositorio,
                          UsuarioActual usuarioActual,
                          TransactionTemplate transaccion) {
        this.repositorio = repositorio;
        this.usuarioActual = usuarioActual;
        this.transaccion = transaccion;
    }

    /**
     * Devuelve el perfil del usuario autenticado, creándolo si es la primera vez que entra.
     *
     * <p>Esto es el auto-aprovisionamiento: no hay registro manual. Si el dominio del correo no
     * está autorizado, {@link UsuarioActual#obtener()} lanza la excepción correspondiente y el
     * perfil no se crea.</p>
     */
    public PerfilPrivadoResponse obtenerOCrearPerfilPropio() {
        return sobreMiPerfil(PerfilPrivadoResponse::desde);
    }

    /**
     * Actualiza el perfil del usuario autenticado.
     */
    public PerfilPrivadoResponse actualizarPerfilPropio(ActualizarPerfilRequest solicitud) {
        return sobreMiPerfil(usuario -> {
            usuario.actualizarPerfil(
                    solicitud.nombre(),
                    solicitud.carrera(),
                    solicitud.sede(),
                    solicitud.bio(),
                    solicitud.telefono(),
                    solicitud.redes());
            return PerfilPrivadoResponse.desde(usuario);
        });
    }

    /**
     * Alterna la visibilidad del perfil propio en los listados públicos.
     *
     * @return el nuevo valor de visibilidad
     */
    public boolean alternarVisibilidadPropia() {
        return sobreMiPerfil(Usuario::alternarVisibilidad);
    }

    /**
     * Devuelve las redes sociales del usuario autenticado.
     *
     * <p>Solo las propias: las de terceros quedan sujetas al consentimiento mutuo (EP2).</p>
     */
    public List<String> obtenerRedesPropias() {
        return sobreMiPerfil(usuario -> List.copyOf(usuario.getRedes()));
    }

    /**
     * Busca el perfil público de otra persona.
     *
     * @throws UsuarioNoEncontradoException si no existe o está oculto
     */
    public PerfilPublicoResponse buscarPerfilPublico(UUID id) {
        return transaccion.execute(estado -> repositorio.findByIdAndVisibleIsTrue(id)
                .map(PerfilPublicoResponse::desde)
                .orElseThrow(() -> new UsuarioNoEncontradoException(id)));
    }

    /**
     * Lista los perfiles visibles, con filtros opcionales por carrera y sede.
     */
    public Page<PerfilPublicoResponse> listarPerfilesVisibles(String carrera, String sede,
                                                              Pageable paginacion) {
        return transaccion.execute(estado -> repositorio
                .buscarVisibles(vacioComoNulo(carrera), vacioComoNulo(sede), paginacion)
                .map(PerfilPublicoResponse::desde));
    }

    /**
     * Busca el perfil por su identificador de Azure AD y lo crea si no existe.
     *
     * <p>Se llama siempre desde dentro de una transacción abierta por quien invoca.</p>
     */
    private Usuario obtenerOCrear(UsuarioActual.IdentidadUsuario identidad) {
        return repositorio.findByOidEntra(identidad.oid())
                .map(existente -> sincronizar(existente, identidad))
                .orElseGet(() -> crearPerfil(identidad));
    }

    /**
     * Crea el perfil por primera vez.
     *
     * <p>Dos peticiones simultáneas del mismo login pueden intentar crearlo a la vez y chocar
     * contra el índice único de {@code oid_entra}. Cuando eso pasa, PostgreSQL deja la transacción
     * en curso abortada y ya no acepta más consultas en ella, así que la segunda búsqueda se hace
     * en una transacción nueva.</p>
     */
    private Usuario crearPerfil(UsuarioActual.IdentidadUsuario identidad) {
        Usuario nuevo = new Usuario(
                identidad.oid(), identidad.nombre(), identidad.correo(), identidad.rol());
        // Se usa saveAndFlush para que el choque contra el índice único salte acá y no
        // más tarde, cuando ya no se sabría qué operación lo provocó.
        Usuario guardado = repositorio.saveAndFlush(nuevo);
        log.info("Perfil auto-aprovisionado para el rol {} en el dominio {}.",
                identidad.rol(), dominioDe(identidad.correo()));
        return guardado;
    }

    /**
     * Ejecuta una operación sobre el perfil del usuario autenticado, dentro de una transacción,
     * creando el perfil si es su primer ingreso.
     *
     * <p>Si dos peticiones simultáneas del mismo usuario intentan crearlo a la vez, una choca
     * contra el índice único. PostgreSQL deja esa transacción abortada y no acepta más consultas
     * en ella, así que el reintento se hace en una transacción nueva: para entonces el perfil ya
     * existe y la segunda vuelta simplemente lo encuentra.</p>
     */
    private <T> T sobreMiPerfil(java.util.function.Function<Usuario, T> accion) {
        UsuarioActual.IdentidadUsuario identidad = usuarioActual.obtener();
        try {
            return transaccion.execute(estado -> accion.apply(obtenerOCrear(identidad)));
        } catch (DataIntegrityViolationException creacionSimultanea) {
            log.debug("El perfil lo creó otra petición simultánea; se reintenta.");
            return transaccion.execute(estado -> accion.apply(obtenerOCrear(identidad)));
        }
    }

    /**
     * Refresca los datos que vienen del token, por si cambiaron en Azure AD desde el último acceso.
     */
    private Usuario sincronizar(Usuario existente, UsuarioActual.IdentidadUsuario identidad) {
        existente.sincronizarDesdeToken(identidad.nombre(), identidad.correo(), identidad.rol());
        return existente;
    }

    /** Convierte los filtros vacíos o en blanco a nulo. */
    private static String vacioComoNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    /** Saca el dominio del correo para registrarlo en el log sin exponer el correo entero. */
    private static String dominioDe(String correo) {
        int posicionArroba = correo.lastIndexOf('@');
        return posicionArroba >= 0 ? correo.substring(posicionArroba + 1) : "desconocido";
    }
}
