package com.ganera.core.ganadero;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardia de M2 (escalon 2, T0): {@code DescifradoCredencialOvz} es el UNICO sitio de produccion que descifra la
 * contrasena OVZ. {@code CifradorAesGcm.descifrar} es publico (cualquier bean podria inyectar el cifrador y
 * descifrar sin dejar la fila de auditoria {@code CREDENCIAL_OVZ_DESCIFRADA} ni comprobar la gestoria); este test
 * lo vigila. {@code cifrar} NO se restringe: guardar credenciales (alta/edicion) cifra en otros sitios y es legitimo.
 *
 * <p>Escaneo de BYTECODE (no de texto) de {@code target/classes} con el ASM que reempaqueta spring-core, igual que
 * {@code whatsapp.ConsultasSinTenantTest}: detecta llamadas directas y referencias de metodo
 * ({@code cifrador::descifrar}, que compilan a {@code invokedynamic}) y no se confunde con Javadoc/comentarios. La
 * propia clase {@code CifradorAesGcm} que DECLARA {@code descifrar} no se cuenta (no se invoca a si misma). Anadir
 * una llamada a {@code descifrar} desde cualquier otra clase pone el test en ROJO.
 */
class DescifradorUnicoTest {

    private static final Path CLASES = Path.of("target", "classes");
    private static final String PERMITIDO = "com/ganera/core/ganadero/DescifradoCredencialOvz";

    @Test
    void soloDescifradoCredencialOvzLlamaADescifrar() throws IOException {
        Set<String> infractores = new TreeSet<>();
        try (Stream<Path> clases = Files.walk(CLASES)) {
            clases.filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> analizar(p, infractores));
        }
        assertThat(infractores).isEmpty();
    }

    private static void analizar(Path clase, Set<String> infractores) {
        String nombreClase = internalName(clase);
        if (nombreClase.equals(PERMITIDO)) {
            return; // el unico autorizado a descifrar
        }
        try (InputStream in = Files.newInputStream(clase)) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int acceso, String nombre, String desc, String firma, String[] exc) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int op, String owner, String name, String d, boolean itf) {
                            registrar(name, owner);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... args) {
                            for (Object arg : args) {
                                if (arg instanceof Handle h) {
                                    registrar(h.getName(), h.getOwner());
                                }
                            }
                        }

                        private void registrar(String name, String owner) {
                            if (name.equals("descifrar") && owner.endsWith("CifradorAesGcm")) {
                                infractores.add(nombreClase + " -> "
                                        + owner.substring(owner.lastIndexOf('/') + 1) + ".descifrar");
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String internalName(Path clase) {
        Path relativo = CLASES.relativize(clase);
        String s = relativo.toString().replace('\\', '/');
        return s.substring(0, s.length() - ".class".length());
    }
}
