package com.ganera.core.alta;

/**
 * Plantillas (en español) de los dos emails del alta automatica. Clase pura, sin Spring, para poder probar el
 * contenido sin enviar nada (igual que NormalizadorBusqueda / ClasificadorEnvioWhatsapp).
 *
 * <p>El de bienvenida lleva la contrasena temporal en claro (va directo al cliente que acaba de pagar); el de
 * aviso al equipo NUNCA lleva contrasena ni datos de tarjeta, solo los datos del alta.
 */
public final class PlantillasEmailAlta {

    private PlantillasEmailAlta() {
    }

    /** Un email listo para enviar: asunto + cuerpo HTML. */
    public record Email(String asunto, String cuerpoHtml) {
    }

    /**
     * Email de bienvenida al cliente: enlace a la app, email de acceso, contrasena temporal, aviso de cambiarla
     * al entrar y los 15 dias de prueba.
     */
    public static Email bienvenida(String nombre, String emailAcceso, String passwordTemporal, String urlApp) {
        String saludo = (nombre == null || nombre.isBlank()) ? "Hola" : "Hola " + escapar(nombre.trim());
        String cuerpo = "<p>" + saludo + ",</p>"
                + "<p>Tu cuenta de <strong>Ganera</strong> ya está lista. Tienes <strong>15 días de prueba</strong> "
                + "para empezar a usarla.</p>"
                + "<p>Entra en <a href=\"" + escapar(urlApp) + "\">" + escapar(urlApp) + "</a> con estos datos:</p>"
                + "<ul>"
                + "<li>Email de acceso: <strong>" + escapar(emailAcceso) + "</strong></li>"
                + "<li>Contraseña temporal: <strong>" + escapar(passwordTemporal) + "</strong></li>"
                + "</ul>"
                + "<p><strong>Por seguridad, cambia la contraseña en cuanto entres.</strong></p>"
                + "<p>Un saludo,<br>El equipo de Ganera</p>";
        return new Email("Bienvenido a Ganera: datos de acceso", cuerpo);
    }

    /**
     * Aviso interno al equipo de una nueva alta automatica. Solo los datos del alta (nombre, email, NIF, plan);
     * nunca contrasena ni datos de tarjeta.
     */
    public static Email equipo(String nombre, String email, String nif, String plan) {
        String cuerpo = "<p>Nueva alta automática desde la landing:</p>"
                + "<ul>"
                + "<li>Nombre: " + escapar(valorOGuion(nombre)) + "</li>"
                + "<li>Email: " + escapar(valorOGuion(email)) + "</li>"
                + "<li>NIF: " + escapar(valorOGuion(nif)) + "</li>"
                + "<li>Plan: " + escapar(valorOGuion(plan)) + "</li>"
                + "</ul>";
        return new Email("Nueva alta automática en Ganera", cuerpo);
    }

    private static String valorOGuion(String valor) {
        return (valor == null || valor.isBlank()) ? "—" : valor.trim();
    }

    /** Escape HTML minimo para no romper el markup ni inyectar etiquetas con datos del cliente. */
    private static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
