package cl.duoc.duocconecta.bff.controller;

import cl.duoc.duocconecta.bff.dto.MiPerfilResponse;
import cl.duoc.duocconecta.bff.service.UsuariosClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de agregación que consume el frontend React.
 */
@RestController
@RequestMapping("/api/v1/bff")
@Tag(name = "BFF", description = "Respuestas agregadas para las pantallas del frontend")
@SecurityRequirement(name = "bearer-jwt")
public class PerfilBffController {

    /** Roles que pueden operar sobre su propio perfil. */
    private static final String ROLES_DE_LA_PLATAFORMA =
            "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    private final UsuariosClient usuariosClient;

    public PerfilBffController(UsuariosClient usuariosClient) {
        this.usuariosClient = usuariosClient;
    }

    /**
     * Devuelve todo lo que la pantalla de perfil necesita en una sola llamada.
     *
     * <p>Por dentro consulta a ms-usuarios dos veces (el perfil y las redes) propagando el token
     * del usuario, y junta ambas respuestas. Así el navegador hace una petición en vez de dos.</p>
     *
     * @return 200 con el perfil, las redes y un indicador de si falta completar datos
     */
    @Operation(
            summary = "Devuelve el perfil y las redes del usuario autenticado",
            description = """
                    Agrega en una sola respuesta lo que la pantalla de perfil necesita: los datos
                    del perfil y las redes sociales del usuario autenticado. Por dentro llama a
                    ms-usuarios propagando el mismo token que trajo la petición, así el
                    microservicio resuelve los permisos con la identidad real de la persona.

                    Si es el primer ingreso, el perfil se auto-provisiona en ese momento. El campo
                    perfilIncompleto indica si todavía faltan carrera, sede o biografía, para que
                    el frontend pueda invitar a completarlos.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil y redes obtenidos correctamente"),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido", content = @Content),
            @ApiResponse(responseCode = "403", description = "El correo no pertenece a un dominio institucional autorizado", content = @Content)
    })
    @GetMapping("/mi-perfil")
    @PreAuthorize(ROLES_DE_LA_PLATAFORMA)
    public ResponseEntity<MiPerfilResponse> obtenerMiPerfil() {
        return ResponseEntity.ok(MiPerfilResponse.de(
                usuariosClient.obtenerPerfilPropio(),
                usuariosClient.obtenerRedesPropias()));
    }
}
