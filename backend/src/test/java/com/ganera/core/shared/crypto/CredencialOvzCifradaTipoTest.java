package com.ganera.core.shared.crypto;

import com.ganera.core.ganadero.Ganadero;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2, garantia de tipo de los escritores: no existe NINGUNA via publica para crear un
 * {@link CredencialOvzCifrada} a partir de un String arbitrario. El unico productor es
 * {@link CifradorAesGcm#cifrar(String)}. Este test vigila la ESTRUCTURA que lo sostiene, para que abrirla por
 * descuido (hacer publico el record, anadir una fabrica, quitar el sellado, anadir un setter String) ponga la suite
 * en rojo.
 *
 * <p>La entidad no reconstruye el tipo desde la columna: Hibernate hidrata el campo String directamente (acceso por
 * campo) y el getter devuelve el ciphertext tal cual; el tipo solo existe en la via de escritura.
 */
class CredencialOvzCifradaTipoTest {

    /**
     * Lista CERRADA de los metodos de {@link Ganadero} que reciben un String. Es la UNICA fuente: la vigilan tanto
     * {@link #losUnicosMetodosDeGanaderoQueRecibenUnStringSonLosSettersConocidos} (no hay ningun otro, ni con otro
     * nombre que escriba el ciphertext) como {@link #ningunSetterStringDeGanaderoTocaElCiphertextOvz} (por
     * comportamiento, con centinela). Anadir un campo String a Ganadero obliga a anadir aqui su setter, y solo
     * despues de revisar que no toca la contrasena OVZ.
     */
    private static final List<String> SETTERS_STRING = List.of("setNombre", "setNif", "setOvzUsuario", "setTelefono");

    /** Clave AES-256 valida (32 bytes), INVENTADA solo para el test. */
    private static final String CLAVE_VALIDA =
            Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
    private static final String CENTINELA = "CENTINELA-que-no-debe-acabar-en-el-ciphertext";

    @Test
    void laInterfazEsSelladaYSoloPermiteLaImplementacionDelPaquete() {
        assertThat(CredencialOvzCifrada.class.isSealed()).isTrue();
        assertThat(CredencialOvzCifrada.class.getPermittedSubclasses())
                .containsExactly(CredencialOvzCifradaImpl.class);
    }

    @Test
    void laImplementacionNoEsPublicaNiTieneConstructoresPublicos() {
        assertThat(Modifier.isPublic(CredencialOvzCifradaImpl.class.getModifiers())).isFalse();
        for (Constructor<?> constructor : CredencialOvzCifradaImpl.class.getDeclaredConstructors()) {
            assertThat(Modifier.isPublic(constructor.getModifiers()))
                    .as("constructor %s", constructor)
                    .isFalse();
        }
    }

    @Test
    void laInterfazNoOfreceFabricasEstaticas() {
        for (Method metodo : CredencialOvzCifrada.class.getDeclaredMethods()) {
            assertThat(Modifier.isStatic(metodo.getModifiers())).as("metodo %s", metodo).isFalse();
        }
    }

    @Test
    void elUnicoMetodoPublicoDelPaqueteQueDevuelveElTipoEsCifrar() {
        List<String> productores = new ArrayList<>();
        for (Class<?> clase : List.of(CifradorAesGcm.class, CredencialOvzCifrada.class, EncryptionKeyHolder.class)) {
            for (Method metodo : clase.getDeclaredMethods()) {
                if (Modifier.isPublic(metodo.getModifiers())
                        && CredencialOvzCifrada.class.isAssignableFrom(metodo.getReturnType())) {
                    productores.add(clase.getSimpleName() + "." + metodo.getName());
                }
            }
        }
        assertThat(productores).containsExactly("CifradorAesGcm.cifrar");
    }

    @Test
    void ganaderoNoTieneNingunSetterDeLaContrasenaQueAcepteUnString() {
        List<Method> setters = Arrays.stream(Ganadero.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("setOvzPasswordCifrada"))
                .toList();

        assertThat(setters).hasSize(1);
        assertThat(setters.get(0).getParameterTypes()).containsExactly(CredencialOvzCifrada.class);
    }

    /**
     * Un constructor con argumentos (o un {@code @AllArgsConstructor}/{@code @Builder} de Lombok) podria escribir
     * el campo de la contrasena con un String, sin pasar por el setter tipado.
     */
    @Test
    void elUnicoConstructorDeGanaderoEsElVacio() {
        Constructor<?>[] constructores = Ganadero.class.getDeclaredConstructors();

        assertThat(constructores).hasSize(1);
        assertThat(constructores[0].getParameterCount()).isZero();
    }

    /**
     * Lista CERRADA de los metodos de Ganadero que reciben un String: un setter con otro nombre
     * (p. ej. {@code setOvzPassword(String)}) escribiria el ciphertext saltandose el tipo.
     *
     * <p>Cuando la "opcion B" del alta de Ganadero anada un campo String (p. ej. el telefono), este test DEBE
     * ponerse en rojo: la lista {@link #SETTERS_STRING} se amplia solo tras revisar que el metodo nuevo no toca
     * la contrasena OVZ.
     */
    @Test
    void losUnicosMetodosDeGanaderoQueRecibenUnStringSonLosSettersConocidos() {
        List<String> conParametroString = Arrays.stream(Ganadero.class.getDeclaredMethods())
                .filter(m -> Arrays.asList(m.getParameterTypes()).contains(String.class))
                .map(Method::getName)
                .toList();

        assertThat(conParametroString).containsExactlyInAnyOrderElementsOf(SETTERS_STRING);
    }

    /**
     * Comportamiento, no solo estructura: para CADA setter String conocido ({@link #SETTERS_STRING}), invocarlo con
     * un centinela NUNCA cambia el ciphertext de la contrasena OVZ — ni cuando no hay credencial (sigue null) ni
     * cuando la hay puesta con {@code CifradorAesGcm.cifrar} (sigue exactamente igual), y el ciphertext nunca
     * contiene el centinela. Asi, si se renombra un setter sobre el campo equivocado o se anade uno que escriba la
     * contrasena, este test lo caza aunque el de estructura siga verde.
     */
    @Test
    void ningunSetterStringDeGanaderoTocaElCiphertextOvz() throws Exception {
        CredencialOvzCifrada cifrada = new CifradorAesGcm(CLAVE_VALIDA).cifrar("password-ovz-de-prueba");

        for (String nombreSetter : SETTERS_STRING) {
            Method setter = Ganadero.class.getDeclaredMethod(nombreSetter, String.class);

            // (a) Ganadero sin credencial: el ciphertext sigue null tras invocar el setter con el centinela.
            Ganadero sinCredencial = new Ganadero();
            setter.invoke(sinCredencial, CENTINELA);
            assertThat(sinCredencial.getOvzPasswordCifrada()).as("%s deja el ciphertext null", nombreSetter).isNull();

            // (b) Ganadero con credencial: el ciphertext queda exactamente igual y nunca contiene el centinela.
            Ganadero conCredencial = new Ganadero();
            conCredencial.setOvzPasswordCifrada(cifrada);
            setter.invoke(conCredencial, CENTINELA);
            assertThat(conCredencial.getOvzPasswordCifrada())
                    .as("%s no cambia el ciphertext", nombreSetter)
                    .isEqualTo(cifrada.valor());
            assertThat(conCredencial.getOvzPasswordCifrada())
                    .as("%s no filtra el centinela al ciphertext", nombreSetter)
                    .doesNotContain(CENTINELA);
        }
    }
}
