package cl.duoc.duocconecta.comun.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lee los datos del usuario desde el JWT de Azure AD.
 *
 * <p>Existe para que ningún otro punto del código tenga que saber en qué claim viene cada dato.
 * Azure AD emite claims distintos según el tipo de cuenta y la configuración del tenant, así que
 * acá se prueba una cadena de alternativas configurable.</p>
 */
public class TokenClaims {

    private final PropiedadesSeguridad propiedades;

    public TokenClaims(PropiedadesSeguridad propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * Devuelve el identificador único e inmutable del usuario dentro del tenant.
     *
     * <p>Es el claim {@code oid}. Se prefiere sobre el correo porque el correo puede cambiar
     * (por matrimonio, corrección de nombre, cambio de rol) y el {@code oid} no. Si el token no
     * lo trae, se cae al {@code sub}, que cumple la misma función.</p>
     */
    public Optional<String> oid(Jwt token) {
        return primerClaimConTexto(token, List.of("oid", "sub"));
    }

    /**
     * Devuelve el correo institucional del usuario.
     *
     * <p>Busca en los claims configurados en {@code duocconecta.seguridad.claims-correo},
     * en orden.</p>
     */
    public Optional<String> correo(Jwt token) {
        return primerClaimConTexto(token, propiedades.getClaimsCorreo())
                .map(correo -> correo.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Devuelve el nombre para mostrar del usuario.
     *
     * <p>Si el token no trae ningún claim de nombre, se usa la parte local del correo como
     * respaldo, para que el perfil nunca quede sin nombre.</p>
     */
    public String nombre(Jwt token) {
        return primerClaimConTexto(token, propiedades.getClaimsNombre())
                .orElseGet(() -> correo(token)
                        .map(correo -> correo.substring(0, correo.lastIndexOf('@')))
                        .orElse("Usuario sin nombre"));
    }

    /**
     * Devuelve los App Roles que Azure AD asignó al usuario, si el tenant los usa.
     *
     * <p>Puede venir vacío: en ese caso el rol se deriva del dominio del correo.</p>
     */
    public List<String> rolesDelToken(Jwt token) {
        Object valor = token.getClaim(propiedades.getClaimRoles());
        if (valor instanceof Collection<?> coleccion) {
            return coleccion.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(rol -> !rol.isBlank())
                    .toList();
        }
        // Algunos tenants emiten un único rol como texto plano en vez de una lista.
        if (valor instanceof String texto && !texto.isBlank()) {
            return List.of(texto);
        }
        return List.of();
    }

    /**
     * Devuelve el correo del token o falla si no está.
     *
     * @throws CorreoNoPresenteException si ningún claim configurado trae el correo
     */
    public String correoObligatorio(Jwt token) {
        return correo(token).orElseThrow(
                () -> new CorreoNoPresenteException(propiedades.getClaimsCorreo()));
    }

    /**
     * Recorre una lista de claims y devuelve el primero que tenga texto no vacío.
     */
    private Optional<String> primerClaimConTexto(Jwt token, List<String> nombresDeClaim) {
        if (nombresDeClaim == null) {
            return Optional.empty();
        }
        for (String nombre : nombresDeClaim) {
            String valor = token.getClaimAsString(nombre);
            if (valor != null && !valor.isBlank()) {
                return Optional.of(valor);
            }
        }
        return Optional.empty();
    }
}
