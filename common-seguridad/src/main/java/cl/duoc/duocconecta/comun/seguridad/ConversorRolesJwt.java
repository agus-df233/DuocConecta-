package cl.duoc.duocconecta.comun.seguridad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Convierte el JWT de Azure AD en una autenticación de Spring Security con el rol del usuario
 * como authority.
 *
 * <p>Sigue dos caminos, en este orden:</p>
 * <ol>
 *   <li>Si el token trae App Roles de Azure AD (claim {@code roles}), se usan esos.</li>
 *   <li>Si no, el rol se deriva del dominio del correo institucional.</li>
 * </ol>
 *
 * <p>Si el dominio no está autorizado, el usuario queda <strong>sin ninguna authority</strong>.
 * Eso hace que los endpoints con {@code @PreAuthorize} respondan 403 automáticamente, que es
 * exactamente el comportamiento que se busca para las cuentas externas.</p>
 */
public class ConversorRolesJwt implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TokenClaims tokenClaims;
    private final ResolvedorRol resolvedorRol;

    public ConversorRolesJwt(TokenClaims tokenClaims, ResolvedorRol resolvedorRol) {
        this.tokenClaims = tokenClaims;
        this.resolvedorRol = resolvedorRol;
    }

    /**
     * Construye la autenticación a partir del token ya validado.
     *
     * <p>Como nombre del principal se usa el correo si está, y si no el {@code oid}. Así los logs
     * y los mensajes de error resultan legibles.</p>
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        List<GrantedAuthority> authorities = new ArrayList<>(calcularAuthorities(token));

        String nombrePrincipal = tokenClaims.correo(token)
                .or(() -> tokenClaims.oid(token))
                .orElse(token.getSubject());

        return new JwtAuthenticationToken(token, authorities, nombrePrincipal);
    }

    /**
     * Calcula las authorities del usuario: primero desde los App Roles, si no desde el dominio.
     */
    private List<GrantedAuthority> calcularAuthorities(Jwt token) {
        List<GrantedAuthority> desdeAppRoles = authoritiesDesdeAppRoles(token);
        if (!desdeAppRoles.isEmpty()) {
            return desdeAppRoles;
        }
        return authoritiesDesdeDominio(token);
    }

    /**
     * Traduce los App Roles del token a authorities.
     *
     * <p>Solo se aceptan los valores que coinciden con un rol conocido de la plataforma; cualquier
     * otro se ignora, para que un rol suelto configurado en Azure AD no otorgue permisos aquí.</p>
     */
    private List<GrantedAuthority> authoritiesDesdeAppRoles(Jwt token) {
        return tokenClaims.rolesDelToken(token).stream()
                .map(this::aRolConocido)
                .flatMap(Optional::stream)
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(rol.comoAuthority()))
                .distinct()
                .toList();
    }

    /**
     * Deriva la authority del dominio del correo. Si el dominio no está autorizado, devuelve
     * una lista vacía y el usuario queda sin permisos.
     */
    private List<GrantedAuthority> authoritiesDesdeDominio(Jwt token) {
        return tokenClaims.correo(token)
                .flatMap(resolvedorRol::resolverPorCorreo)
                .map(rol -> List.<GrantedAuthority>of(new SimpleGrantedAuthority(rol.comoAuthority())))
                .orElseGet(List::of);
    }

    /**
     * Convierte el texto de un App Role al enum {@link Rol}, tolerando mayúsculas y minúsculas.
     */
    private Optional<Rol> aRolConocido(String nombreRol) {
        try {
            return Optional.of(Rol.valueOf(nombreRol.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException noEsUnRolDeLaPlataforma) {
            return Optional.empty();
        }
    }
}
