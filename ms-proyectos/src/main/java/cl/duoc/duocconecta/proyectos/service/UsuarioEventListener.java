package cl.duoc.duocconecta.proyectos.service;

import static cl.duoc.duocconecta.mensajeria.MensajeriaConstantes.COLA_PUBLICACIONES_DLQ;
import static cl.duoc.duocconecta.mensajeria.MensajeriaConstantes.COLA_PUBLICACIONES_USUARIO_CREADO;

import cl.duoc.duocconecta.mensajeria.UsuarioCreadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UsuarioEventListener {

    private static final Logger log = LoggerFactory.getLogger(UsuarioEventListener.class);

    @RabbitListener(queues = COLA_PUBLICACIONES_USUARIO_CREADO)
    public void onUsuarioCreado(UsuarioCreadoEvent evento) {
        log.info("Evento usuario.creado recibido: {}", evento.id());
        // aquí va tu lógica, por ejemplo guardar una copia local del autor
    }

    @RabbitListener(queues = COLA_PUBLICACIONES_DLQ)
    public void onMensajeMuerto(Message mensaje) {
        log.error("Mensaje no entregado enviado a DLQ. Cola origen: {}, motivo: {}, cuerpo: {}",
                mensaje.getMessageProperties().getHeader("x-first-death-queue"),
                mensaje.getMessageProperties().getHeader("x-first-death-reason"),
                new String(mensaje.getBody()));
    }
}