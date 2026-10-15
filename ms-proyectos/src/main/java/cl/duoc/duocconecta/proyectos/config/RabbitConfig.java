package cl.duoc.duocconecta.proyectos.config;

import static cl.duoc.duocconecta.mensajeria.MensajeriaConstantes.*;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange usuariosExchange() {
        return new TopicExchange(EXCHANGE_USUARIOS);
    }

    @Bean
    DirectExchange dlx() {
        return new DirectExchange(EXCHANGE_DLX);
    }

    @Bean
    Queue colaUsuarioCreado() {
        return QueueBuilder.durable(COLA_PUBLICACIONES_USUARIO_CREADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", RK_PUBLICACIONES_DLQ)
                .build();
    }

    @Bean
    Queue colaDlq() {
        return QueueBuilder.durable(COLA_PUBLICACIONES_DLQ).build();
    }

    @Bean
    Binding bindingUsuarioCreado() {
        return BindingBuilder.bind(colaUsuarioCreado())
                .to(usuariosExchange()).with(RK_USUARIO_CREADO);
    }

    @Bean
    Binding bindingDlq() {
        return BindingBuilder.bind(colaDlq()).to(dlx()).with(RK_PUBLICACIONES_DLQ);
    }

    @Bean
    MessageConverter jsonConverter() {
        return new JacksonJsonMessageConverter();
    }
}