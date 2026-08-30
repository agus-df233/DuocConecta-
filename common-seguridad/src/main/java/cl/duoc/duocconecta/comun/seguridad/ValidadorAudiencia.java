package cl.duoc.duocconecta.comun.seguridad;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Verifica que el token haya sido emitido para esta API y no para otra aplicación del tenant.
 *
 * <p>Spring valida por defecto la firma, la vigencia y el emisor, pero no la audiencia. Sin esta
 * comprobación, un token válido emitido para cualquier otra aplicación del mismo tenant de Azure AD
 * sería aceptado. Es el error clásico de "confused deputy".</p>
 *
 * <p>El error típico acá es que el frontend haya pedido solo los scopes de OIDC
 * ({@code openid profile}): en ese caso Microsoft devuelve un ID token cuya audiencia es el
 * client-id del SPA, no el de la API, y este validador lo rechaza.</p>
 */
public class ValidadorAudiencia implements OAuth2TokenValidator<Jwt> {

    /** Audiencia esperada: el client-id del registro de la API en Azure AD. */
    private final String audienciaEsperada;

    public ValidadorAudiencia(String audienciaEsperada) {
        this.audienciaEsperada = audienciaEsperada;
    }

    /**
     * Comprueba que la audiencia esperada esté entre las del token.
     *
     * <p>El claim {@code aud} puede traer varios valores, por eso se busca en la lista completa.
     * También se acepta la forma {@code api://<client-id>} porque Azure AD la usa en algunos
     * tokens según cómo esté configurado el Application ID URI.</p>
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiencias = token.getAudience();

        if (audiencias != null
                && (audiencias.contains(audienciaEsperada)
                    || audiencias.contains("api://" + audienciaEsperada))) {
            return OAuth2TokenValidatorResult.success();
        }

        // No se incluye la audiencia recibida en el mensaje: iría al cliente y no aporta
        // nada a quien no debería conocer la configuración del tenant.
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "El token no fue emitido para esta API. Verificá que el cliente esté pidiendo el "
                        + "scope de la API (api://<client-id>/access_as_user) y no solo openid/profile.",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
