package dbmanager;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 *
 * @author elean
 */
public class ConexionPg {
    private Connection conn;

    public Connection conectar(String host, String puerto, String base, String usuario, String pass) {
        try {
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://" + host + ":" + puerto + "/" + base;
            conn = DriverManager.getConnection(url, usuario, pass);
            System.out.println("✅ Conectado a PostgreSQL");
        } catch (Exception e) {
            System.err.println("❌ PG conexión: " + e.getMessage());
        }
        return conn;
    }

    public Connection get() { return conn; }

    public void cerrar() {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
    }
}