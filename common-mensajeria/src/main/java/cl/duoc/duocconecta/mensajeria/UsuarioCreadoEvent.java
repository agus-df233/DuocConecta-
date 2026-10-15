package cl.duoc.duocconecta.mensajeria;

import java.util.UUID;

public record UsuarioCreadoEvent(UUID id, String nombre, String correo, String rol) {}