package cl.duoc.duocconecta.usuarios.controller;

import cl.duoc.duocconecta.usuarios.dto.ActualizarPerfilRequest;
import cl.duoc.duocconecta.usuarios.dto.PerfilPrivadoResponse;
import cl.duoc.duocconecta.usuarios.dto.PerfilPublicoResponse;
import cl.duoc.duocconecta.usuarios.dto.RedesResponse;
import cl.duoc.duocconecta.usuarios.dto.VisibilidadResponse;
import cl.duoc.duocconecta.usuarios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de perfiles de usuario de DuocConecta.
 *
 * <p>Todos los endpoints exigen un token válido de Azure AD. Los que operan sobre el perfil propio
 * ({@code /me}) exigen además un rol de la plataforma, que se deriva del dominio del correo: una
 * cuenta de un dominio no autorizado recibe 403 sin llegar a crear perfil.</p>
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Perfiles, visibilidad y datos de contacto de la comunidad Duoc UC")
@SecurityRequirement(name = "bearer-jwt")
public class UsuarioController {

    /** Cantidad de perfiles por página cuando el cliente no pide otra cosa. */
    private static final int TAMANO_PAGINA_POR_DEFECTO = 20;

    /** Tope de perfiles por página, para que nadie pueda pedir la tabla entera de una vez. */
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    /** Roles que pueden operar sobre su propio perfil. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    /**
     * Devuelve el perfil de la persona autenticada y lo crea si es su primer ingreso.
     *
     * <p>Los datos iniciales (identificador, correo y nombre) se toman de los claims del token, no
     * de la petición. El rol se deduce del dominio del correo; si ese dominio no está autorizado,
     * el perfil no se crea y la respuesta es 403.</p>
     *
     * @return 200 con el perfil completo, incluidos los datos de contacto propios
     */
    @Operation(
            summary = "Obtiene o auto-provisiona el perfil propio",
            description = """
                    Devuelve el perfil de la persona autenticada. Si es su primer ingreso, lo crea
                    automáticamente a partir de los claims del token (oid, correo y nombre) y le
                    asigna el rol según el dominio de su correo institucional:
                    @duocuc.cl es ESTUDIANTE, @profesor.duoc.cl es PROFESOR y @duoc.cl es ACADEMICO.
                    Incluye los datos de contacto propios (teléfono y redes), que nunca aparecen en
                    las respuestas públicas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil obtenido o creado correctamente"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/me")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<PerfilPrivadoResponse> obtenerPerfilPropio() {
        return ResponseEntity.ok(servicio.obtenerOCrearPerfilPropio());
    }

    /**
     * Actualiza los datos editables del perfil propio.
     *
     * <p>Solo se pueden cambiar nombre, carrera, sede, biografía, teléfono y redes. El correo, el
     * rol y el identificador de Azure AD vienen del token y no son modificables.</p>
     *
     * @param solicitud campos nuevos del perfil, ya validados
     * @return 200 con el perfil actualizado
     */
    @Operation(
            summary = "Actualiza el perfil propio",
            description = """
                    Modifica nombre, carrera, sede, biografía, teléfono y redes sociales de la
                    persona autenticada. El correo, el rol y el identificador de Azure AD no se
                    pueden cambiar porque se toman del token. Devuelve el perfil ya actualizado.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
            @ApiResponse(responseCode = "400", description = "Los datos enviados no pasaron la validación", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PutMapping("/me")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<PerfilPrivadoResponse> actualizarPerfilPropio(
            @Valid @RequestBody ActualizarPerfilRequest solicitud) {
        return ResponseEntity.ok(servicio.actualizarPerfilPropio(solicitud));
    }

    /**
     * Muestra u oculta el perfil propio en las búsquedas.
     *
     * <p>Es un interruptor: cada llamada deja la visibilidad en el estado contrario. Ocultarse no
     * borra nada, solo saca el perfil de los listados públicos.</p>
     *
     * @return 200 con el nuevo estado de visibilidad
     */
    @Operation(
            summary = "Alterna la visibilidad del perfil propio",
            description = """
                    Cambia la visibilidad del perfil al estado contrario: si estaba visible lo
                    oculta, y si estaba oculto lo vuelve a mostrar. Ocultarse no borra el perfil ni
                    sus datos, solo lo saca de las búsquedas y listados públicos. Devuelve el nuevo
                    estado junto con una explicación en palabras.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visibilidad actualizada"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PatchMapping("/me/visibilidad")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<VisibilidadResponse> alternarVisibilidad() {
        return ResponseEntity.ok(VisibilidadResponse.de(servicio.alternarVisibilidadPropia()));
    }

    /**
     * Devuelve las redes sociales de la persona autenticada.
     *
     * <p>Solo las propias. Las de otras personas son datos de contacto privados y quedan sujetas
     * al consentimiento mutuo, que se implementa en EP2.</p>
     *
     * @return 200 con el listado de redes, que puede venir vacío
     */
    @Operation(
            summary = "Devuelve las redes sociales del usuario autenticado",
            description = """
                    Entrega los enlaces a redes sociales que la propia persona cargó en su perfil.
                    Solo devuelve las propias: las de terceros son datos de contacto privados y
                    quedarán sujetas al consentimiento mutuo. El listado puede venir vacío si la
                    persona todavía no cargó ninguna.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de redes del usuario autenticado"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/me/redes")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<RedesResponse> obtenerRedesPropias() {
        return ResponseEntity.ok(new RedesResponse(servicio.obtenerRedesPropias()));
    }

    /**
     * Devuelve el perfil público de otra persona.
     *
     * <p>Nunca incluye teléfono ni redes. Si la persona ocultó su perfil, la respuesta es 404: se
     * responde igual que si no existiera, para no delatar su presencia en la plataforma.</p>
     *
     * @param id identificador del perfil
     * @return 200 con el perfil público
     */
    @Operation(
            summary = "Obtiene el perfil público de un usuario",
            description = """
                    Devuelve el perfil de otra persona tal como lo ve la comunidad: nombre, rol,
                    carrera, sede y biografía. Nunca incluye teléfono ni redes sociales, que son
                    datos de contacto privados. Si la persona ocultó su perfil se responde 404,
                    igual que si no existiera, para no revelar que está en la plataforma.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil público encontrado"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "El perfil no existe o está oculto", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<PerfilPublicoResponse> obtenerPerfilPublico(
            @Parameter(description = "Identificador del perfil a consultar")
            @PathVariable UUID id) {
        return ResponseEntity.ok(servicio.buscarPerfilPublico(id));
    }

    /**
     * Lista los perfiles visibles de la comunidad, con filtros opcionales.
     *
     * <p>Es lo que alimenta la vitrina. Solo aparecen los perfiles marcados como visibles y sin
     * datos de contacto.</p>
     *
     * @param carrera filtro opcional por carrera
     * @param sede    filtro opcional por sede
     * @param pagina  número de página, empezando en cero
     * @param tamano  cantidad de perfiles por página
     * @return 200 con el listado de perfiles públicos
     */
    @Operation(
            summary = "Lista los perfiles públicos visibles",
            description = """
                    Devuelve los perfiles de la comunidad que están marcados como visibles, para
                    alimentar la vitrina. Acepta filtros opcionales por carrera y por sede, que se
                    comparan sin distinguir mayúsculas. El resultado viene paginado y nunca incluye
                    teléfono ni redes sociales.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de perfiles visibles"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping
    public ResponseEntity<List<PerfilPublicoResponse>> listarPerfiles(
            @Parameter(description = "Filtra por carrera. Si se omite, no filtra.", example = "Ingeniería en Informática")
            @RequestParam(required = false) String carrera,

            @Parameter(description = "Filtra por sede. Si se omite, no filtra.", example = "Plaza Oeste")
            @RequestParam(required = false) String sede,

            @Parameter(description = "Número de página, empezando en cero")
            @RequestParam(defaultValue = "0") int pagina,

            @Parameter(description = "Cantidad de perfiles por página (máximo 100)")
            @RequestParam(defaultValue = "20") int tamano) {

        Pageable paginacion = PageRequest.of(Math.max(pagina, 0), acotarTamano(tamano));
        Page<PerfilPublicoResponse> resultado = servicio.listarPerfilesVisibles(carrera, sede, paginacion);

        return ResponseEntity.ok(resultado.getContent());
    }

    /**
     * Deja el tamaño de página dentro de un rango razonable, para que una petición no pueda
     * pedir la tabla completa.
     */
    private static int acotarTamano(int tamanoPedido) {
        if (tamanoPedido <= 0) {
            return TAMANO_PAGINA_POR_DEFECTO;
        }
        return Math.min(tamanoPedido, TAMANO_PAGINA_MAXIMO);
    }
}
