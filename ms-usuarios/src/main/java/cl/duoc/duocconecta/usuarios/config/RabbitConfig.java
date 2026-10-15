package cl.duoc.duocconecta.usuarios.config;

import cl.duoc.duocconecta.mensajeria.MensajeriaConstantes;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange usuariosExchange() {
        return new TopicExchange(MensajeriaConstantes.EXCHANGE_USUARIOS);
    }

    @Bean
    MessageConverter jsonConverter() {
        return new JacksonJsonMessageConverter();
    }
}