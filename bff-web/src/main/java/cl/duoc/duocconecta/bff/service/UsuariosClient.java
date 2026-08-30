package cl.duoc.duocconecta.bff.service;

import cl.duoc.duocconecta.bff.dto.PerfilUsuarioDto;
import cl.duoc.duocconecta.bff.dto.RedesDto;
import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Cliente del BFF hacia ms-usuarios.
 *
 * <p>Propaga el mismo token que trajo la petición original en vez de usar una credencial propia
 * del BFF. Así el microservicio decide los permisos con la identidad real de la persona, y el BFF
 * nunca puede pedir más de lo que el usuario puede.</p>
 */
@Service
public class UsuariosClient {

    private static final String RUTA_PERFIL_PROPIO = "/api/v1/usuarios/me";
    private static final String RUTA_REDES_PROPIAS = "/api/v1/usuarios/me/redes";

    private final RestClient cliente;
    private final UsuarioActual usuarioActual;

    public UsuariosClient(RestClient clienteMsUsuarios, UsuarioActual usuarioActual) {
        this.cliente = clienteMsUsuarios;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Pide a ms-usuarios el perfil del usuario autenticado.
     *
     * <p>Si es su primer ingreso, el microservicio lo crea en el momento.</p>
     */
    public PerfilUsuarioDto obtenerPerfilPropio() {
        return cliente.get()
                .uri(RUTA_PERFIL_PROPIO)
                .header(HttpHeaders.AUTHORIZATION, cabeceraAuthorization())
                .retrieve()
                .body(PerfilUsuarioDto.class);
    }

    /**
     * Pide a ms-usuarios las redes sociales del usuario autenticado.
     */
    public List<String> obtenerRedesPropias() {
        RedesDto respuesta = cliente.get()
                .uri(RUTA_REDES_PROPIAS)
                .header(HttpHeaders.AUTHORIZATION, cabeceraAuthorization())
                .retrieve()
                .body(RedesDto.class);

        return (respuesta == null || respuesta.redes() == null) ? List.of() : respuesta.redes();
    }

    /**
     * Rearma la cabecera Authorization con el token original de la petición en curso.
     */
    private String cabeceraAuthorization() {
        return "Bearer " + usuarioActual.tokenActual().getTokenValue();
    }
}
