package dbmanager;
/**
 *
 * @author elean
 */
public class ConexionInfo {
    public String nombre;
    public String host;
    public String puerto;
    public String baseDatos;
    public String usuario;
    public String contrasena;

    public ConexionInfo() {}

    public ConexionInfo(String nombre, String host, String puerto,
                        String baseDatos, String usuario, String contrasena) {
        this.nombre = nombre;
        this.host = host;
        this.puerto = puerto;
        this.baseDatos = baseDatos;
        this.usuario = usuario;
        this.contrasena = contrasena;
    }

    @Override
    public String toString() {
        return nombre + " (" + host + ":" + puerto + "/" + baseDatos + ")";
    }
}
