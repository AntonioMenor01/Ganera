package com.ganera.core.whatsapp;

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
 * Guardia de las consultas DELIBERADAMENTE sin scope de tenant (plan 2026-11-16-whatsapp-numero-por-gestoria, T2).
 * {@code NumeroWhatsappRepository.findByNumero} (resolucion de la gestoria por numero de DESTINO) y
 * {@code ContactoRepository.findByTelefono} (resolucion del remitente) se saltan el aislamiento por gestoria a
 * proposito: solo pueden usarse desde el webhook de recepcion ({@code MensajeEntranteService}). Cualquier otra ruta
 * debe usar los finders con {@code gestoriaId}.
 *
 * <p>Escaneo de BYTECODE (no de texto) de {@code target/classes} con el ASM que reempaqueta spring-core: detecta
 * tanto las llamadas directas como las referencias de metodo ({@code repo::findByNumero}, que compilan a
 * {@code invokedynamic}), y NO se confunde con las menciones en comentarios/Javadoc. La interfaz que DECLARA el
 * metodo no lo invoca, asi que no aparece. Anadir una llamada desde otro bean pone el test en ROJO.
 */
class ConsultasSinTenantTest {

    private static final Path CLASES = Path.of("target", "classes");
    private static final String PERMITIDO = "com/ganera/core/whatsapp/MensajeEntranteService";
    private static final Set<String> METODOS_SIN_TENANT = Set.of("findByNumero", "findByTelefono");

    @Test
    void soloElWebhookDeRecepcionUsaLosFindersSinTenant() throws IOException {
        Set<String> infractores = new TreeSet<>();
        try (Stream<Path> clases = Files.walk(CLASES)) {
            clases.filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> analizar(p, infractores));
        }
        // Solo MensajeEntranteService puede llamar a findByNumero/findByTelefono.
        assertThat(infractores).isEmpty();
    }

    private static void analizar(Path clase, Set<String> infractores) {
        String nombreClase = internalName(clase);
        if (nombreClase.equals(PERMITIDO)) {
            return; // el unico autorizado
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
                            // repo::findByNumero compila a invokedynamic con un Handle al metodo entre los bootstrap args.
                            for (Object arg : args) {
                                if (arg instanceof Handle h) {
                                    registrar(h.getName(), h.getOwner());
                                }
                            }
                        }

                        private void registrar(String name, String owner) {
                            if (METODOS_SIN_TENANT.contains(name) && owner.endsWith("Repository")) {
                                infractores.add(nombreClase + " -> " + owner.substring(owner.lastIndexOf('/') + 1)
                                        + "." + name);
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
