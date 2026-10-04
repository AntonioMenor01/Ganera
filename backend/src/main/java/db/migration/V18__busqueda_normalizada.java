package db.migration;

import com.ganera.core.shared.texto.NormalizadorBusqueda;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Columnas normalizadas para la busqueda de explotaciones sin tildes y por palabras
 * (GET /explotaciones?q=): ganadero.nombre_busqueda = NormalizadorBusqueda.normalizar(nombre) y
 * explotacion.busqueda = NormalizadorBusqueda.textoExplotacion(codigo_rega, nombre). A partir de
 * aqui las mantienen los setters de las entidades Ganadero y Explotacion.
 *
 * Es Java (no SQL) para rellenar las filas existentes con exactamente la misma regla que las
 * entidades: anade las columnas nullable, recorre las filas y las actualiza en lotes, y despues
 * las deja NOT NULL. Solo JDBC: nada de entidades, repositorios ni Spring (NormalizadorBusqueda es
 * una funcion pura). VARCHAR sin longitud porque NFKD puede alargar el texto. Funciona igual en
 * PostgreSQL y en H2 (MODE=PostgreSQL); en PostgreSQL corre entera en una transaccion.
 *
 * Si algun dia cambia la regla de NormalizadorBusqueda, hace falta una migracion NUEVA que
 * recalcule las columnas: esta no se vuelve a ejecutar (las migraciones Java no tienen checksum
 * de contenido, asi que Flyway tampoco avisara de que la regla cambio).
 */
public class V18__busqueda_normalizada extends BaseJavaMigration {

    private static final int TAMANO_LOTE = 500;

    @Override
    public void migrate(Context context) throws SQLException {
        Connection conexion = context.getConnection();
        try (Statement st = conexion.createStatement()) {
            st.execute("ALTER TABLE ganadero ADD COLUMN nombre_busqueda VARCHAR");
            st.execute("ALTER TABLE explotacion ADD COLUMN busqueda VARCHAR");
        }

        rellenarGanaderos(conexion);
        rellenarExplotaciones(conexion);

        try (Statement st = conexion.createStatement()) {
            st.execute("ALTER TABLE ganadero ALTER COLUMN nombre_busqueda SET NOT NULL");
            st.execute("ALTER TABLE explotacion ALTER COLUMN busqueda SET NOT NULL");
        }
    }

    private static void rellenarGanaderos(Connection conexion) throws SQLException {
        try (Statement select = conexion.createStatement();
             ResultSet filas = select.executeQuery("SELECT id, nombre FROM ganadero");
             PreparedStatement update = conexion.prepareStatement(
                     "UPDATE ganadero SET nombre_busqueda = ? WHERE id = ?")) {
            int pendientes = 0;
            while (filas.next()) {
                update.setString(1, NormalizadorBusqueda.normalizar(filas.getString("nombre")));
                update.setLong(2, filas.getLong("id"));
                update.addBatch();
                if (++pendientes == TAMANO_LOTE) {
                    update.executeBatch();
                    pendientes = 0;
                }
            }
            if (pendientes > 0) {
                update.executeBatch();
            }
        }
    }

    private static void rellenarExplotaciones(Connection conexion) throws SQLException {
        try (Statement select = conexion.createStatement();
             ResultSet filas = select.executeQuery("SELECT id, codigo_rega, nombre FROM explotacion");
             PreparedStatement update = conexion.prepareStatement(
                     "UPDATE explotacion SET busqueda = ? WHERE id = ?")) {
            int pendientes = 0;
            while (filas.next()) {
                update.setString(1, NormalizadorBusqueda.textoExplotacion(
                        filas.getString("codigo_rega"), filas.getString("nombre")));
                update.setLong(2, filas.getLong("id"));
                update.addBatch();
                if (++pendientes == TAMANO_LOTE) {
                    update.executeBatch();
                    pendientes = 0;
                }
            }
            if (pendientes > 0) {
                update.executeBatch();
            }
        }
    }
}
