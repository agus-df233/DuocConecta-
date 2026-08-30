package cl.duoc.duocconecta.usuarios.repository;

import cl.duoc.duocconecta.usuarios.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acceso a los perfiles de usuario en el schema {@code usuarios}.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * Busca el perfil por el identificador de Azure AD.
     *
     * <p>Es la búsqueda que usa el auto-aprovisionamiento: el {@code oid} es lo único que no
     * cambia nunca para una persona dentro del tenant.</p>
     */
    Optional<Usuario> findByOidEntra(String oidEntra);

    /** Busca el perfil por correo institucional. */
    Optional<Usuario> findByCorreo(String correo);

    /**
     * Lista los perfiles visibles, filtrando opcionalmente por carrera y sede.
     *
     * <p>Los filtros se ignoran cuando llegan nulos, así el mismo query sirve para las cuatro
     * combinaciones posibles sin tener que armar la consulta a mano.</p>
     *
     * <p>La comparación es sin distinguir mayúsculas para que "Ingeniería" y "ingeniería"
     * devuelvan lo mismo.</p>
     */
    @Query("""
            SELECT u FROM Usuario u
            WHERE u.visible = true
              AND (:carrera IS NULL OR LOWER(u.carrera) = LOWER(:carrera))
              AND (:sede    IS NULL OR LOWER(u.sede)    = LOWER(:sede))
            ORDER BY u.nombre ASC
            """)
    Page<Usuario> buscarVisibles(@Param("carrera") String carrera,
                                 @Param("sede") String sede,
                                 Pageable paginacion);

    /**
     * Busca un perfil por id solo si está visible.
     *
     * <p>Se usa para el perfil público: si la persona se ocultó, para el resto del mundo es como
     * si no existiera.</p>
     */
    Optional<Usuario> findByIdAndVisibleIsTrue(UUID id);
}
