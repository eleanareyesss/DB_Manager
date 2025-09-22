package dbmanager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 *
 * @author elean
 */
public class Conexion {

    private Connection connection;

    public Connection conectar(String host, String puerto, String baseDatos, String usuario, String contrasena) {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            String url = "jdbc:mariadb://" + host + ":" + puerto + "/" + baseDatos;
            connection = DriverManager.getConnection(url, usuario, contrasena);
            System.out.println("✅ Conexión exitosa a la base de datos");
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("❌ Error de conexión: " + e.getMessage());
        }
        return connection;
    }

    public Connection getConnection() {
        return connection;
    }

    public void cerrar() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Conexión cerrada.");
            }
        } catch (SQLException e) {
            System.err.println("Error al cerrar conexión: " + e.getMessage());
        }
    }
}
