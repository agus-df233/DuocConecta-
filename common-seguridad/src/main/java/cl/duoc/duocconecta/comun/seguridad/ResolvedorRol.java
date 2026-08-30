package cl.duoc.duocconecta.comun.seguridad;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce el dominio de un correo institucional al rol que le corresponde.
 *
 * <p>Es el único lugar del sistema donde vive esa regla. Los dominios se leen de la
 * configuración ({@code duocconecta.seguridad.dominios}), así que sumar uno nuevo es editar
 * el YAML, no el código.</p>
 */
public class ResolvedorRol {

    /** Copia normalizada del mapa configurado: claves en minúsculas y sin espacios. */
    private final Map<String, Rol> dominiosNormalizados;

    public ResolvedorRol(PropiedadesSeguridad propiedades) {
        Map<String, Rol> normalizados = new LinkedHashMap<>();
        propiedades.getDominios().forEach((dominio, rol) ->
                normalizados.put(normalizar(dominio), rol));
        this.dominiosNormalizados = Map.copyOf(normalizados);
    }

    /**
     * Determina el rol a partir del correo completo.
     *
     * @param correo correo institucional, por ejemplo {@code juan.perez@duocuc.cl}
     * @return el rol correspondiente, o vacío si el dominio no está autorizado
     */
    public Optional<Rol> resolverPorCorreo(String correo) {
        return extraerDominio(correo).flatMap(this::resolverPorDominio);
    }

    /**
     * Determina el rol a partir del dominio suelto.
     *
     * @param dominio dominio sin arroba, por ejemplo {@code duocuc.cl}
     * @return el rol correspondiente, o vacío si no está autorizado
     */
    public Optional<Rol> resolverPorDominio(String dominio) {
        if (dominio == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dominiosNormalizados.get(normalizar(dominio)));
    }

    /**
     * Saca el dominio de un correo: todo lo que viene después del último arroba.
     *
     * <p>Se usa el último arroba y no el primero porque la parte local de un correo puede
     * contener uno si va entre comillas, y porque así el resultado nunca queda a medias.</p>
     *
     * @param correo correo completo
     * @return el dominio en minúsculas, o vacío si el correo no tiene forma de correo
     */
    public Optional<String> extraerDominio(String correo) {
        if (correo == null || correo.isBlank()) {
            return Optional.empty();
        }
        int posicionArroba = correo.lastIndexOf('@');
        // Se descarta si no hay arroba, si está al principio (sin parte local)
        // o si está al final (sin dominio).
        if (posicionArroba <= 0 || posicionArroba == correo.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(normalizar(correo.substring(posicionArroba + 1)));
    }

    /**
     * Deja el dominio en una forma comparable: sin espacios alrededor y en minúsculas.
     *
     * <p>Importante: la comparación contra el mapa es por igualdad exacta, nunca por sufijo.
     * Si se usara {@code endsWith}, un correo {@code @profesor.duoc.cl} también coincidiría con
     * {@code duoc.cl} y el rol dependería del orden de iteración del mapa.</p>
     */
    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim().toLowerCase(Locale.ROOT);
    }
}
