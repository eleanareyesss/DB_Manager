package dbmanager;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import javax.swing.*;
import java.sql.*;
import java.util.*;

public class DiagramaER extends JFrame {

    public DiagramaER(String baseDatos) {
        super("Diagrama ER - " + baseDatos);

        mxGraph graph = new mxGraph();
        Object parent = graph.getDefaultParent();

        Map<String, Object> nodos = new HashMap<>();

        graph.getModel().beginUpdate();
        try {
            Statement stmt = Global.conexion.createStatement();

            // 🔹 Obtener todas las tablas
            ResultSet rsTablas = stmt.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'");
            int x = 20, y = 20;

            while (rsTablas.next()) {
                String tabla = rsTablas.getString(1);

                // 🔹 Obtener columnas con PK y FK
                StringBuilder label = new StringBuilder(tabla + "\n");
                ResultSet rsCols = stmt.executeQuery("SHOW COLUMNS FROM `" + tabla + "`");
                while (rsCols.next()) {
                    String col = rsCols.getString("Field");
                    String key = rsCols.getString("Key"); // PRI / MUL
                    if ("PRI".equals(key)) {
                        label.append("🔑 ").append(col).append("\n");
                    } else if ("MUL".equals(key)) {
                        label.append("🔗 ").append(col).append("\n");
                    } else {
                        label.append(col).append("\n");
                    }
                }

                // 🔹 Crear nodo en el grafo
                Object nodo = graph.insertVertex(parent, null, label.toString(), x, y, 180, 120);
                nodos.put(tabla, nodo);

                x += 220;
                if (x > 700) {
                    x = 20;
                    y += 180;
                }
            }

            // 🔹 Obtener claves foráneas desde SHOW CREATE TABLE
            rsTablas.beforeFirst(); // volver al inicio
            while (rsTablas.next()) {
                String tabla = rsTablas.getString(1);

                ResultSet rsDDL = stmt.executeQuery("SHOW CREATE TABLE `" + tabla + "`");
                if (rsDDL.next()) {
                    String ddl = rsDDL.getString(2);
                    for (String line : ddl.split("\n")) {
                        line = line.trim();
                        if (line.startsWith("CONSTRAINT") && line.contains("FOREIGN KEY")) {
                            // Ejemplo: CONSTRAINT `fk_insc_est` FOREIGN KEY (`id_estudiante`) REFERENCES `estudiante` (`id_estudiante`)
                            int refIndex = line.indexOf("REFERENCES");
                            if (refIndex > 0) {
                                String refPart = line.substring(refIndex);
                                String[] parts = refPart.split("`");
                                if (parts.length >= 2) {
                                    String tablaRef = parts[1];
                                    if (nodos.containsKey(tabla) && nodos.containsKey(tablaRef)) {
                                        graph.insertEdge(parent, null, "FK", nodos.get(tabla), nodos.get(tablaRef));
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error generando diagrama: " + e.getMessage());
            e.printStackTrace();
        } finally {
            graph.getModel().endUpdate();
        }

        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        getContentPane().add(graphComponent);
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);
    }
}