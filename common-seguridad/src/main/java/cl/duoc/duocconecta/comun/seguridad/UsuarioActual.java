package cl.duoc.duocconecta.comun.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Punto único para obtener los datos del usuario autenticado.
 *
 * <p>La identidad se toma siempre del token, nunca de un parámetro de la petición. Si el cliente
 * pudiera mandar su propio {@code oid} o correo, cualquiera podría leer o modificar el perfil
 * ajeno con solo cambiar un valor en la URL.</p>
 */
public class UsuarioActual {

    private final TokenClaims tokenClaims;
    private final ResolvedorRol resolvedorRol;

    public UsuarioActual(TokenClaims tokenClaims, ResolvedorRol resolvedorRol) {
        this.tokenClaims = tokenClaims;
        this.resolvedorRol = resolvedorRol;
    }

    /**
     * Devuelve la identidad del usuario que hizo la petición, ya validada.
     *
     * @return los datos del usuario tomados del token
     * @throws IllegalStateException si no hay un token en el contexto (no debería pasar:
     *         la cadena de seguridad ya habría respondido 401)
     * @throws CorreoNoPresenteException si el token no trae el correo
     * @throws DominioNoPermitidoException si el dominio del correo no está autorizado
     */
    public IdentidadUsuario obtener() {
        Jwt token = tokenActual();

        String correo = tokenClaims.correoObligatorio(token);
        String dominio = resolvedorRol.extraerDominio(correo)
                .orElseThrow(() -> new DominioNoPermitidoException(correo));

        Rol rol = resolvedorRol.resolverPorDominio(dominio)
                .orElseThrow(() -> new DominioNoPermitidoException(dominio));

        String oid = tokenClaims.oid(token)
                .orElseThrow(() -> new IllegalStateException(
                        "El token no trae el claim 'oid' ni 'sub'; no se puede identificar al usuario."));

        return new IdentidadUsuario(oid, correo, tokenClaims.nombre(token), rol);
    }

    /**
     * Devuelve el token crudo de la petición en curso.
     */
    public Jwt tokenActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new IllegalStateException(
                "No hay un token JWT en el contexto de seguridad para la petición en curso.");
    }

    /**
     * Datos del usuario autenticado, ya extraídos y validados desde el token.
     *
     * @param oid    identificador inmutable del usuario en el tenant (claim {@code oid})
     * @param correo correo institucional en minúsculas
     * @param nombre nombre para mostrar
     * @param rol    rol derivado de los App Roles o del dominio del correo
     */
    public record IdentidadUsuario(String oid, String correo, String nombre, Rol rol) {
    }
}
