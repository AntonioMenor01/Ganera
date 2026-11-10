package com.ganera.core.shared.crypto;

/**
 * Texto cifrado de una credencial OVZ (M2, garantia de tipo de los escritores). Interfaz sellada cuya UNICA
 * implementacion ({@link CredencialOvzCifradaImpl}) es package-private, de modo que solo {@link CifradorAesGcm}
 * (mismo paquete) puede crear un valor: el setter de {@code Ganadero} acepta este tipo y nada mas, asi que pasar
 * un {@code String} plano al setter no compila. Garantia estructural, no runtime.
 *
 * <p>Cualquier paquete puede referenciar este tipo y leer {@link #valor()} (el ciphertext en reposo), pero no
 * construir una instancia.
 */
public sealed interface CredencialOvzCifrada permits CredencialOvzCifradaImpl {

    /** El ciphertext en reposo (Base64 de IV de 12 bytes + AES-256-GCM). */
    String valor();
}
