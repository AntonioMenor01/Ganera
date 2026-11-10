package com.ganera.core.shared.crypto;

/**
 * Unica implementacion de {@link CredencialOvzCifrada}. Package-private a proposito: solo {@link CifradorAesGcm}
 * (mismo paquete) la crea, a traves de {@link CifradorAesGcm#cifrar(String)}.
 */
record CredencialOvzCifradaImpl(String valor) implements CredencialOvzCifrada {
}
