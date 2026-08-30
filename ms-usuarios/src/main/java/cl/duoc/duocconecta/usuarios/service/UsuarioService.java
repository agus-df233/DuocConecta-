package cl.duoc.duocconecta.usuarios.service;

import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.usuarios.domain.Usuario;
import cl.duoc.duocconecta.usuarios.dto.ActualizarPerfilRequest;
import cl.duoc.duocconecta.usuarios.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de los perfiles de usuario.
 *
 * <p>La identidad siempre se toma del token a través de {@link UsuarioActual}; ningún método
 * recibe el {@code oid} o el correo por parámetro, para que nadie pueda operar sobre el perfil
 * ajeno cambiando un valor de la petición.</p>
 */
@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository repositorio;
    private final UsuarioActual usuarioActual;

    public UsuarioService(UsuarioRepository repositorio, UsuarioActual usuarioActual) {
        this.repositorio = repositorio;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Devuelve el perfil del usuario autenticado, creándolo si es la primera vez que entra.
     *
     * <p>Esto es el auto-aprovisionamiento: no hay registro manual. Si el dominio del correo no
     * está autorizado, {@link UsuarioActual#obtener()} lanza la excepción correspondiente y el
     * perfil no se crea.</p>
     *
     * @return el perfil, recién creado o ya existente
     */
    @Transactional
    public Usuario obtenerOCrearPerfilPropio() {
        UsuarioActual.IdentidadUsuario identidad = usuarioActual.obtener();

        return repositorio.findByOidEntra(identidad.oid())
                .map(existente -> sincronizar(existente, identidad))
                .orElseGet(() -> crearPerfil(identidad));
    }

    /**
     * Actualiza el perfil del usuario autenticado.
     */
    @Transactional
    public Usuario actualizarPerfilPropio(ActualizarPerfilRequest solicitud) {
        Usuario usuario = obtenerOCrearPerfilPropio();
        usuario.actualizarPerfil(
                solicitud.nombre(),
                solicitud.carrera(),
                solicitud.sede(),
                solicitud.bio(),
                solicitud.telefono(),
                solicitud.redes());
        return usuario;
    }

    /**
     * Alterna la visibilidad del perfil propio en los listados públicos.
     *
     * @return el nuevo valor de visibilidad
     */
    @Transactional
    public boolean alternarVisibilidadPropia() {
        return obtenerOCrearPerfilPropio().alternarVisibilidad();
    }

    /**
     * Devuelve las redes sociales del usuario autenticado.
     *
     * <p>Solo las propias: las de terceros quedan sujetas al consentimiento mutuo (EP2).</p>
     */
    @Transactional
    public List<String> obtenerRedesPropias() {
        return List.copyOf(obtenerOCrearPerfilPropio().getRedes());
    }

    /**
     * Busca el perfil público de otra persona.
     *
     * @param id identificador del perfil
     * @return el perfil, si existe y está visible
     * @throws UsuarioNoEncontradoException si no existe o está oculto
     */
    @Transactional(readOnly = true)
    public Usuario buscarPerfilPublico(UUID id) {
        return repositorio.findByIdAndVisibleIsTrue(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException(id));
    }

    /**
     * Lista los perfiles visibles, con filtros opcionales por carrera y sede.
     *
     * <p>Los filtros vacíos se normalizan a nulo para que la consulta los ignore.</p>
     */
    @Transactional(readOnly = true)
    public Page<Usuario> listarPerfilesVisibles(String carrera, String sede, Pageable paginacion) {
        return repositorio.buscarVisibles(vacioComoNulo(carrera), vacioComoNulo(sede), paginacion);
    }

    /**
     * Crea el perfil por primera vez.
     *
     * <p>Dos peticiones simultáneas del mismo login podrían intentar crear el perfil a la vez y
     * chocar contra el índice único de {@code oid_entra}. En ese caso se vuelve a buscar: la otra
     * transacción ya lo creó.</p>
     */
    private Usuario crearPerfil(UsuarioActual.IdentidadUsuario identidad) {
        Usuario nuevo = new Usuario(
                identidad.oid(), identidad.nombre(), identidad.correo(), identidad.rol());
        try {
            Usuario guardado = repositorio.saveAndFlush(nuevo);
            log.info("Perfil auto-aprovisionado para el rol {} en el dominio {}.",
                    identidad.rol(), dominioDe(identidad.correo()));
            return guardado;
        } catch (DataIntegrityViolationException creacionSimultanea) {
            log.debug("El perfil ya había sido creado por otra petición simultánea; se reutiliza.");
            return repositorio.findByOidEntra(identidad.oid())
                    .orElseThrow(() -> creacionSimultanea);
        }
    }

    /**
     * Refresca los datos que vienen del token, por si cambiaron en Azure AD desde el último acceso
     * (por ejemplo, una corrección del nombre o un cambio de rol).
     */
    private Usuario sincronizar(Usuario existente, UsuarioActual.IdentidadUsuario identidad) {
        existente.sincronizarDesdeToken(identidad.nombre(), identidad.correo(), identidad.rol());
        return existente;
    }

    /** Convierte los filtros vacíos o en blanco a nulo. */
    private static String vacioComoNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    /** Saca el dominio del correo para poder registrarlo en el log sin exponer el correo entero. */
    private static String dominioDe(String correo) {
        int posicionArroba = correo.lastIndexOf('@');
        return posicionArroba >= 0 ? correo.substring(posicionArroba + 1) : "desconocido";
    }
}
