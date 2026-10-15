package cl.duoc.duocconecta.usuarios.service;

import cl.duoc.duocconecta.mensajeria.MensajeriaConstantes;
import cl.duoc.duocconecta.mensajeria.UsuarioCreadoEvent;
import cl.duoc.duocconecta.usuarios.domain.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class UsuarioEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UsuarioEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UsuarioEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarUsuarioCreado(Usuario u) {
        var evento = new UsuarioCreadoEvent(u.getId(), u.getNombre(), u.getCorreo(), u.getRol().name());
        rabbitTemplate.convertAndSend(
                MensajeriaConstantes.EXCHANGE_USUARIOS,
                MensajeriaConstantes.RK_USUARIO_CREADO,
                evento);
        log.info("Evento usuario.creado publicado: {}", u.getId());
    }
}