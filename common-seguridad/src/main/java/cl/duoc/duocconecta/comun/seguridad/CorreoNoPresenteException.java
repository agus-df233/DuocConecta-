package cl.duoc.duocconecta.comun.seguridad;

import java.io.Serial;

/**
 * Se lanza cuando el token es válido pero no trae ningún claim con el correo del usuario.
 *
 * <p>Casi siempre significa que falta configurar los claims opcionales del access token en el
 * registro de la aplicación en Azure AD. Sin correo no se puede determinar el rol, así que la
 * petición se rechaza con 403.</p>
 */
public class CorreoNoPresenteException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CorreoNoPresenteException(java.util.List<String> claimsBuscados) {
        super("El token no trae el correo del usuario. Se buscó en los claims " + claimsBuscados
                + ". Revisá los claims opcionales del access token en el registro de la app en Azure AD.");
    }
}
