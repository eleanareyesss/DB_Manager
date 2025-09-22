package dbmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author elean
 */
public class Global {
    public static Connection conexion;                   // conexión activa
    public static List<ConexionInfo> conexiones = new ArrayList<>();
    public static ConexionInfo conexionActual;           // puntero a la activa
    public static String ultimaConexionNombre;           // para autoselección

    private static final Path CONF_DIR  =
            Paths.get(System.getProperty("user.home"), ".mini-dbmanager");
    private static final Path FILE_JSON = CONF_DIR.resolve("conexiones.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Cargar lista + última conexión usada
    public static void cargarConexiones() {
        try { Files.createDirectories(CONF_DIR); } catch (IOException ignored) {}
        if (!Files.exists(FILE_JSON)) return;
        try (Reader r = Files.newBufferedReader(FILE_JSON, StandardCharsets.UTF_8)) {
            Persist p = GSON.fromJson(r, Persist.class);
            if (p != null) {
                conexiones = (p.conexiones != null) ? p.conexiones : new ArrayList<>();
                ultimaConexionNombre = p.ultimaConexionNombre;
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Guardar lista + última conexión usada
    public static void guardarConexiones() {
        Persist p = new Persist();
        p.conexiones = conexiones;
        p.ultimaConexionNombre = (conexionActual != null) ? conexionActual.nombre : ultimaConexionNombre;
        try (Writer w = Files.newBufferedWriter(FILE_JSON, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(p, w);
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Conectar (si la BD no existe, se crea)
    public static void conectar(ConexionInfo info) {
        Conexion c = new Conexion();
        conexion = c.conectar(info.host, info.puerto, info.baseDatos, info.usuario, info.contrasena);
        if (conexion == null) {
            // Intentar crear la BD y reconectar
            try {
                Connection tmp = c.conectar(info.host, info.puerto, "", info.usuario, info.contrasena);
                if (tmp != null) {
                    try (Statement st = tmp.createStatement()) {
                        st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + info.baseDatos + "`");
                    }
                    tmp.close();
                    conexion = c.conectar(info.host, info.puerto, info.baseDatos, info.usuario, info.contrasena);
                }
            } catch (Exception ignored) {}
        }
        if (conexion != null) {
            conexionActual = info;
            ultimaConexionNombre = info.nombre;
            guardarConexiones(); // persistimos al quedar conectados
            System.out.println("✅ Conectado a: " + info);
        }
    }

    // Estructura del JSON
    private static class Persist {
        List<ConexionInfo> conexiones;
        String ultimaConexionNombre;
    }
}
