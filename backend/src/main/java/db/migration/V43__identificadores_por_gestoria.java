package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * V43 -- identificadores de negocio UNIQUE por gestoria, no globales.
 *
 * <p>Objetivo: que una segunda gestoria pueda gestionar al MISMO ganadero/explotacion/animal reales.
 * Hasta aqui {@code explotacion.codigo_rega} (V4), {@code animal.crotal} (V5) y {@code ganadero.nif}
 * (V14, {@code uq_ganadero_nif}) eran UNIQUE globales: la segunda gestoria chocaba al importar un
 * identificador que ya tuviera otra. Esta migracion cambia los tres a {@code UNIQUE(gestoria_id, col)},
 * aislados por tenant. {@code contacto.telefono}, {@code usuario.email} y los ids externos (Stripe,
 * Twilio, numero de WhatsApp) se quedan globales a proposito (el telefono por el numero de WhatsApp
 * COMPARTIDO; ver CLAUDE.md).
 *
 * <p><b>Por que Java y no SQL</b>: los UNIQUE de codigo_rega y crotal son inline en la columna (V4/V5)
 * con nombre AUTOGENERADO -- determinista en PostgreSQL ({@code <tabla>_<col>_key}) pero aleatorio en
 * H2 ({@code CONSTRAINT_xxx}). No se pueden soltar por un nombre fijo en un SQL comun a los dos motores.
 * Esta migracion descubre el nombre real por {@code INFORMATION_SCHEMA} (portable H2/PostgreSQL) y
 * suelta, en cada tabla, EXACTAMENTE el UNIQUE de una sola columna (la columna objetivo) -- nunca la PK
 * (tipo PRIMARY KEY), ni las FK (tipo FOREIGN KEY), ni un UNIQUE compuesto (mas de una columna).
 *
 * <p><b>Filas que violen el compuesto</b>: imposibles. Cada UNIQUE global era estrictamente mas fuerte
 * (valor unico en todo el sistema => unico por gestoria). Si por lo imposible existiera una colision,
 * el {@code ADD CONSTRAINT} falla ruidoso (fail-closed), que es lo correcto. SELECT de verificacion para
 * Supabase (debe devolver 0 filas por tabla): {@code SELECT gestoria_id, codigo_rega, count(*) FROM
 * explotacion GROUP BY gestoria_id, codigo_rega HAVING count(*) > 1;} (idem crotal/nif).
 */
public class V43__identificadores_por_gestoria extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        Connection conexion = context.getConnection();
        intercambiar(conexion, "explotacion", "codigo_rega", "uq_explotacion_gestoria_codigo_rega");
        intercambiar(conexion, "animal", "crotal", "uq_animal_gestoria_crotal");
        intercambiar(conexion, "ganadero", "nif", "uq_ganadero_gestoria_nif");
    }

    /** Suelta el UNIQUE global de una sola columna sobre {@code tabla(columna)} y anade el compuesto
     * {@code (gestoria_id, columna)} con el nombre acordado. */
    private static void intercambiar(Connection conexion, String tabla, String columna, String nombreCompuesto)
            throws SQLException {
        for (String constraintGlobal : uniquesDeUnaColumna(conexion, tabla, columna)) {
            try (Statement st = conexion.createStatement()) {
                st.execute("ALTER TABLE " + tabla + " DROP CONSTRAINT \"" + constraintGlobal + "\"");
            }
        }
        try (Statement st = conexion.createStatement()) {
            st.execute("ALTER TABLE " + tabla + " ADD CONSTRAINT " + nombreCompuesto
                    + " UNIQUE (gestoria_id, " + columna + ")");
        }
    }

    /**
     * Nombres de las constraints UNIQUE sobre {@code tabla} que cubren EXACTAMENTE la unica columna
     * {@code columna} (ni la PK, ni FKs, ni uniques compuestos). INFORMATION_SCHEMA estandar, valido en
     * H2 2.x y PostgreSQL; {@code lower(...)} en tabla/columna porque H2 guarda los identificadores en
     * mayusculas y PostgreSQL en minusculas.
     */
    private static List<String> uniquesDeUnaColumna(Connection conexion, String tabla, String columna)
            throws SQLException {
        String sql = "SELECT tc.constraint_name "
                + "FROM information_schema.table_constraints tc "
                + "WHERE tc.constraint_type = 'UNIQUE' "
                + "  AND lower(tc.table_name) = ? "
                + "  AND (SELECT count(*) FROM information_schema.key_column_usage k "
                + "       WHERE k.constraint_catalog = tc.constraint_catalog "
                + "         AND k.constraint_schema = tc.constraint_schema "
                + "         AND k.constraint_name = tc.constraint_name) = 1 "
                + "  AND EXISTS (SELECT 1 FROM information_schema.key_column_usage k "
                + "       WHERE k.constraint_catalog = tc.constraint_catalog "
                + "         AND k.constraint_schema = tc.constraint_schema "
                + "         AND k.constraint_name = tc.constraint_name "
                + "         AND lower(k.column_name) = ?)";
        List<String> nombres = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, tabla.toLowerCase());
            ps.setString(2, columna.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nombres.add(rs.getString(1));
                }
            }
        }
        return nombres;
    }
}
