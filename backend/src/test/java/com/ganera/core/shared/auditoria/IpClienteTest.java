package com.ganera.core.shared.auditoria;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IP del cliente para la auditoria (plan 2026-10-21, D5): funcion pura. Con {@code confiarEnXff = false} (local,
 * tests) siempre la de la conexion; con {@code true} (solo el perfil clever) el ULTIMO elemento de X-Forwarded-For,
 * el que anade el proxy de Clever, y solo si es una IP literal.
 */
class IpClienteTest {

    private static final String PROXY = "10.0.0.5";

    private static String conXff(String... cabeceras) {
        return IpCliente.de(PROXY, List.of(cabeceras), true);
    }

    @Test
    void sinCabeceraSeQuedaLaDeLaConexion() {
        assertThat(IpCliente.de(PROXY, List.of(), true)).isEqualTo(PROXY);
        assertThat(IpCliente.de(PROXY, null, true)).isEqualTo(PROXY);
    }

    @Test
    void conUnaCabeceraDeUnElementoSeTomaEse() {
        assertThat(conXff("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(conXff("  203.0.113.7  ")).isEqualTo("203.0.113.7");
    }

    @Test
    void conVariasCabecerasSeUnenEnOrdenYSeTomaElUltimoElemento() {
        assertThat(conXff("198.51.100.1, 198.51.100.2", "203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(conXff("203.0.113.7", "198.51.100.1, 198.51.100.2")).isEqualTo("198.51.100.2");
    }

    @Test
    void unaIpFalsaQueMeteElClienteALaIzquierdaNoSeUsa() {
        assertThat(conXff("1.1.1.1, 203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(conXff("1.1.1.1", "203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void cabeceraVaciaOSoloComasDaLaDeLaConexion() {
        assertThat(conXff("")).isEqualTo(PROXY);
        assertThat(conXff(" , , ")).isEqualTo(PROXY);
        assertThat(conXff(",,,")).isEqualTo(PROXY);
        // El ultimo NO vacio: unas comas detras no cambian nada.
        assertThat(conXff("203.0.113.7, ,")).isEqualTo("203.0.113.7");
    }

    @Test
    void ipv6CompletaYComprimida() {
        assertThat(conXff("2001:0db8:0000:0000:0000:ff00:0042:8329")).isEqualTo("2001:0db8:0000:0000:0000:ff00:0042:8329");
        assertThat(conXff("2001:db8::ff00:42:8329")).isEqualTo("2001:db8::ff00:42:8329");
        assertThat(conXff("::1")).isEqualTo("::1");
        assertThat(conXff("::")).isEqualTo("::");
        assertThat(conXff("fe80::")).isEqualTo("fe80::");
        assertThat(conXff("::ffff:192.0.2.128")).isEqualTo("::ffff:192.0.2.128");
        assertThat(conXff("0:0:0:0:0:ffff:192.0.2.128")).isEqualTo("0:0:0:0:0:ffff:192.0.2.128");
    }

    @Test
    void ipv6MalFormadaSeRechaza() {
        for (String mala : new String[] {"2001:db8::ff00::1", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7", "12345::1",
                "2001:db8:::1", ":1:2:3:4:5:6:7", "1:2:3:4:5:6:7:", "fe80::1%eth0", "::ffff:192.0.2", "gggg::1",
                "1:2:3:4:5:6:7:8::", "::1.2.3.4:5"}) {
            assertThat(conXff(mala)).as(mala).isEqualTo(PROXY);
        }
    }

    @Test
    void seQuitanCorchetesYPuerto() {
        assertThat(conXff("[::1]:443")).isEqualTo("::1");
        assertThat(conXff("[2001:db8::1]")).isEqualTo("2001:db8::1");
        assertThat(conXff("1.2.3.4:5678")).isEqualTo("1.2.3.4");
        assertThat(conXff("[1.2.3.4]:80")).isEqualTo(PROXY); // corchetes solo para IPv6
        assertThat(conXff("1.2.3.4:")).isEqualTo(PROXY);
        assertThat(conXff("1.2.3.4:puerto")).isEqualTo(PROXY);
        assertThat(conXff("[::1]:")).isEqualTo(PROXY);
        assertThat(conXff("[::1")).isEqualTo(PROXY);
    }

    @Test
    void basuraEnElUltimoElementoDaLaDeLaConexionAunqueAntesHayaUnaIpValida() {
        for (String basura : new String[] {"unknown", "_hidden", "proxy.example.com", "1.2.3", "256.1.1.1",
                "1.2.3.4.5", "01.2.3.4", "1.2.3.-4", "-", "1.2.3.4 5.6.7.8", "ES123456789012"}) {
            assertThat(conXff("203.0.113.7, " + basura)).as(basura).isEqualTo(PROXY);
        }
    }

    @Test
    void ipv4EnSusLimites() {
        assertThat(conXff("0.0.0.0")).isEqualTo("0.0.0.0");
        assertThat(conXff("255.255.255.255")).isEqualTo("255.255.255.255");
        assertThat(conXff("1.2.3.0")).isEqualTo("1.2.3.0");
        assertThat(conXff("1.2.3.1000")).isEqualTo(PROXY);
        assertThat(conXff("1.2.3.")).isEqualTo(PROXY);
    }

    @Test
    void sinConfiarEnXffSeIgnoraLaCabecera() {
        assertThat(IpCliente.de(PROXY, List.of("203.0.113.7"), false)).isEqualTo(PROXY);
        assertThat(IpCliente.de("127.0.0.1", List.of("1.1.1.1, 203.0.113.7"), false)).isEqualTo("127.0.0.1");
    }

    @Test
    void elResultadoNuncaPasaDe45Caracteres() {
        // La forma mas larga valida (IPv6 con IPv4 embebida) cabe justo.
        String larga = "ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255";
        assertThat(larga).hasSize(45);
        assertThat(conXff(larga)).isEqualTo(larga);
        // Una remoteAddr rara de mas de 45 se recorta (no viene del cliente, pero la columna es VARCHAR(45)).
        String remotaLarga = "x".repeat(60);
        assertThat(IpCliente.de(remotaLarga, List.of(), false)).hasSize(45);
        assertThat(IpCliente.de(remotaLarga, List.of("basura"), true)).hasSize(45);
        assertThat(conXff("1:" .repeat(40) + "1")).isEqualTo(PROXY);
        assertThat(IpCliente.de(null, List.of(), false)).isNull();
    }

    @Test
    void unNombreDeHostNoSeResuelve() {
        // InetAddress.getByName("localhost") daria 127.0.0.1; aqui un nombre nunca es una IP.
        assertThat(conXff("localhost")).isEqualTo(PROXY);
        long inicio = System.nanoTime();
        assertThat(conXff("no-existe-nunca.ganera.invalid")).isEqualTo(PROXY);
        // Sin consulta DNS: inmediato (una resolucion de un .invalid tarda o falla, nunca devuelve una IP).
        assertThat(System.nanoTime() - inicio).isLessThan(500_000_000L);
    }

    @Test
    void ultimaIpValidaDeXffDiceSiHayIp() {
        assertThat(IpCliente.ultimaIpValidaDeXff(List.of("1.1.1.1, 203.0.113.7"))).contains("203.0.113.7");
        assertThat(IpCliente.ultimaIpValidaDeXff(List.of("203.0.113.7, unknown"))).isEmpty();
        assertThat(IpCliente.ultimaIpValidaDeXff(List.of())).isEmpty();
    }
}
