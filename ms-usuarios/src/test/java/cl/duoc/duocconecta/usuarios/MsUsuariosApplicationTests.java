package cl.duoc.duocconecta.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas básicas de ms-usuarios: que el contexto arranque y que la seguridad esté puesta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MsUsuariosApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private MockMvc mockMvc;

    /**
     * El contexto de Spring levanta sin errores.
     *
     * <p>Es la prueba más barata que existe y la que más problemas atrapa: si falta un bean,
     * si dos configuraciones chocan o si el mapeo de una entidad está mal, falla acá.</p>
     */
    @Test
    @DisplayName("El contexto de la aplicación levanta correctamente")
    void elContextoLevanta() {
        assertThat(contexto).isNotNull();
    }

    /**
     * Una ruta protegida sin token responde 401.
     *
     * <p>Es la comprobación central de la EP1: sin credenciales no se entra.</p>
     */
    @Test
    @DisplayName("GET /api/v1/usuarios/me sin token responde 401")
    void rutaProtegidaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El listado de perfiles también exige token: "público" quiere decir sin datos de contacto,
     * no sin autenticación.
     */
    @Test
    @DisplayName("GET /api/v1/usuarios sin token responde 401")
    void listadoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El endpoint de salud queda abierto para que el monitoreo pueda consultarlo.
     */
    @Test
    @DisplayName("GET /actuator/health responde 200 sin token")
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
