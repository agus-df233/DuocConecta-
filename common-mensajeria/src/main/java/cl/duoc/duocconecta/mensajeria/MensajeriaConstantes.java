package cl.duoc.duocconecta.mensajeria;

public final class MensajeriaConstantes {
    public static final String EXCHANGE_USUARIOS = "usuarios.exchange";
    public static final String RK_USUARIO_CREADO = "usuario.creado";
    public static final String COLA_PUBLICACIONES_USUARIO_CREADO = "publicaciones.usuario-creado";

    public static final String EXCHANGE_DLX = "dlx.duocconecta";
    public static final String COLA_PUBLICACIONES_DLQ = "publicaciones.usuario-creado.dlq";
    public static final String RK_PUBLICACIONES_DLQ = "publicaciones.usuario-creado.dlq";

    private MensajeriaConstantes() {}
}
