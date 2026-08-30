package cl.duoc.duocconecta.comun.seguridad;

/**
 * Roles que puede tener una persona dentro de DuocConecta.
 *
 * <p>El rol se determina a partir del dominio del correo institucional que viene en el token
 * de Azure AD, o del claim de App Roles si el tenant lo emite. Ver {@link ResolvedorRol}.</p>
 */
public enum Rol {

    /** Alumnado. Corresponde al dominio {@code @duocuc.cl}. */
    ESTUDIANTE,

    /** Profesorado. Corresponde al dominio {@code @profesor.duoc.cl}. */
    PROFESOR,

    /** Personal académico y administrativo. Corresponde al dominio {@code @duoc.cl}. */
    ACADEMICO;

    /**
     * Prefijo que Spring Security espera en las authorities para que {@code hasRole(...)}
     * y {@code hasAnyRole(...)} funcionen.
     */
    public static final String PREFIJO_AUTHORITY = "ROLE_";

    /**
     * Devuelve el nombre de la authority correspondiente a este rol.
     *
     * @return por ejemplo {@code ROLE_ESTUDIANTE}
     */
    public String comoAuthority() {
        return PREFIJO_AUTHORITY + name();
    }
}
