package cl.duoc.duocconecta.bff.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP con el que el BFF llama a los microservicios.
 *
 * <p>Lleva timeouts explícitos a propósito: sin ellos, un microservicio que se cuelga dejaría al
 * BFF esperando para siempre y arrastraría al frontend con él. Cuando se agota el tiempo, el
 * manejador de errores devuelve un 503 con un mensaje claro.</p>
 */
@Configuration
public class RestClientConfig {

    /**
     * Arma el cliente apuntando a ms-usuarios, con timeout de conexión y de lectura.
     *
     * <p>Se usa el cliente HTTP del JDK para no sumar una librería más al proyecto.</p>
     */
    @Bean
    public RestClient clienteMsUsuarios(PropiedadesBff propiedades) {
        Duration timeout = Duration.ofSeconds(propiedades.getTimeoutSegundos());

        // Tiempo máximo para establecer la conexión con el microservicio.
        HttpClient clienteHttp = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        // Tiempo máximo de espera de la respuesta una vez conectado.
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteHttp);
        fabrica.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(propiedades.getUrlMsUsuarios())
                .requestFactory(fabrica)
                .build();
    }
}
