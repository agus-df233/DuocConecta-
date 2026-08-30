package cl.duoc.duocconecta.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Pruebas básicas del BFF: que el contexto arranque, que la seguridad esté puesta
 * y que el CORS acepte al frontend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BffWebApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private MockMvc mockMvc;

    /** El contexto de Spring levanta sin errores. */
    @Test
    @DisplayName("El contexto de la aplicación levanta correctamente")
    void elContextoLevanta() {
        assertThat(contexto).isNotNull();
    }

    /**
     * El BFF valida el token igual que los microservicios: sin credenciales, 401.
     */
    @Test
    @DisplayName("GET /api/v1/bff/mi-perfil sin token responde 401")
    void rutaProtegidaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/bff/mi-perfil"))
                .andExpect(status().isUnauthorized());
    }

    /** El endpoint de salud queda abierto para el monitoreo. */
    @Test
    @DisplayName("GET /actuator/health responde 200 sin token")
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    /**
     * El preflight del navegador desde el origen del frontend queda autorizado.
     *
     * <p>Sin esto, la aplicación React no podría llamar al BFF ni siquiera con un token válido.</p>
     */
    @Test
    @DisplayName("El preflight de CORS desde el frontend queda autorizado")
    void corsPermiteElOrigenDelFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/bff/mi-perfil")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    /**
     * Un origen que no está en la lista queda fuera.
     *
     * <p>Es la comprobación de que no se coló un comodín en la configuración de CORS.</p>
     */
    @Test
    @DisplayName("El preflight de CORS desde un origen no declarado se rechaza")
    void corsRechazaUnOrigenDesconocido() throws Exception {
        mockMvc.perform(options("/api/v1/bff/mi-perfil")
                        .header("Origin", "https://sitio-no-autorizado.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
