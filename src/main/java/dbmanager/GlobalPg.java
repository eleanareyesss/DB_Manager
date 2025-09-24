package dbmanager;

import java.sql.Connection;

/**
 *
 * @author elean
 */
public class GlobalPg {
    public static Connection conexionPg;
    public static ConexionInfo conexionPgInfo;

    public static void conectar(ConexionInfo info) {
        ConexionPg c = new ConexionPg();
        conexionPg = c.conectar(info.host, info.puerto, info.baseDatos, info.usuario, info.contrasena);
        if (conexionPg != null) conexionPgInfo = info;
    }
}
