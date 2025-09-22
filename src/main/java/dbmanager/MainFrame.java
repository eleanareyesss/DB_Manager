package dbmanager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.sql.*;
import java.util.Scanner;
import dbmanager.DiagramaER;

/**
 *
 * @author elean
 */
public class MainFrame extends JFrame {

    private JComboBox<String> comboConexiones;
    private JTree treeObjetos;
    private JTextArea txtSQL;
    private JButton btnEjecutar, btnGuardar, btnAbrir;
    private JTable tablaResultados;
    private JTextArea txtOutput;

    public MainFrame() {
        super("Mini-DBManager");
        setLayout(new BorderLayout());
        setSize(1000, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel panelTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelTop.add(new JLabel("Conexión:"));
        comboConexiones = new JComboBox<>();
        panelTop.add(comboConexiones);

        JButton btnNuevaConexion = new JButton("Nueva conexión");
        btnNuevaConexion.addActionListener(e -> crearNuevaConexion());
        panelTop.add(btnNuevaConexion);
        add(panelTop, BorderLayout.NORTH);
        
        JButton btnDiagrama = new JButton("📊 Ver Diagrama ER");
        btnDiagrama.addActionListener(ev -> {
            if (Global.conexion == null) {
                JOptionPane.showMessageDialog(this, "No hay conexión activa.");
                return;
            }
            if (Global.conexionActual != null) {
                new DiagramaER(Global.conexionActual.baseDatos);
            } else {
                JOptionPane.showMessageDialog(this, "No se encontró la base de datos actual.");
            }
        });
        panelTop.add(btnDiagrama);


        Global.cargarConexiones();
        for (ConexionInfo info : Global.conexiones) {
            comboConexiones.addItem(info.toString());
        }

        if (Global.ultimaConexionNombre != null) {
            int idx = indexOfByNombre(Global.ultimaConexionNombre);
            if (idx >= 0) {
                comboConexiones.setSelectedIndex(idx);
                Global.conectar(Global.conexiones.get(idx));
            }
        } else if (!Global.conexiones.isEmpty()) {
            comboConexiones.setSelectedIndex(0);
            Global.conectar(Global.conexiones.get(0));
        }

        comboConexiones.addActionListener(e -> {
            int idx = comboConexiones.getSelectedIndex();
            if (idx >= 0 && idx < Global.conexiones.size()) {
                ConexionInfo info = Global.conexiones.get(idx);
                Global.conectar(info);
                cargarObjetos();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Global.guardarConexiones();
            }
        });

        treeObjetos = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("Objetos BD")));
        JScrollPane scrollTree = new JScrollPane(treeObjetos);
        scrollTree.setPreferredSize(new Dimension(280, 0));
        add(scrollTree, BorderLayout.WEST);
        treeObjetos.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = treeObjetos.getPathForLocation(e.getX(), e.getY());
                    if (path == null) {
                        return;
                    }

                    treeObjetos.setSelectionPath(path);
                    DefaultMutableTreeNode nodo = (DefaultMutableTreeNode) path.getLastPathComponent();
                    String nombreNodo = nodo.toString();
                    TreeNode padre = nodo.getParent();

                    JPopupMenu menu = new JPopupMenu();

                    // 📌 Crear tabla o vista (ya lo tenías)
                    if ("Tablas".equals(nombreNodo)) {
                        JMenuItem crearTabla = new JMenuItem("➕ Crear Tabla");
                        crearTabla.addActionListener(ev -> {
                            CrearTablaDialog dlg = new CrearTablaDialog(MainFrame.this);
                            dlg.setVisible(true);
                            cargarObjetos();
                        });
                        menu.add(crearTabla);
                    } else if ("Vistas".equals(nombreNodo)) {
                        JMenuItem crearVista = new JMenuItem("➕ Crear Vista");
                        crearVista.addActionListener(ev -> {
                            CrearVistaDialog dlg = new CrearVistaDialog(MainFrame.this);
                            dlg.setVisible(true);
                            cargarObjetos();
                        });
                        menu.add(crearVista);
                    }

                    // 📌 Ver DDL de objetos individuales
                    if (padre != null) {
                        String categoria = padre.toString();
                        if ("Tablas".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de tabla");
                            verDDL.addActionListener(ev -> mostrarDDL("TABLE", nombreNodo));
                            menu.add(verDDL);
                        } else if ("Vistas".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de vista");
                            verDDL.addActionListener(ev -> mostrarDDL("VIEW", nombreNodo));
                            menu.add(verDDL);
                        } else if ("Procedimientos".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de procedimiento");
                            verDDL.addActionListener(ev -> mostrarDDL("PROCEDURE", nombreNodo));
                            menu.add(verDDL);
                        } else if ("Funciones".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de función");
                            verDDL.addActionListener(ev -> mostrarDDL("FUNCTION", nombreNodo));
                            menu.add(verDDL);
                        } else if ("Triggers".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de trigger");
                            verDDL.addActionListener(ev -> mostrarDDL("TRIGGER", nombreNodo));
                            menu.add(verDDL);
                        }
                    }

                    if (menu.getComponentCount() > 0) {
                        menu.show(treeObjetos, e.getX(), e.getY());
                    }
                }
            }
        });

        JPanel panelCentro = new JPanel(new BorderLayout());

        txtSQL = new JTextArea(15, 40);
        JScrollPane scrollSQL = new JScrollPane(txtSQL);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        btnEjecutar = new JButton("▶");
        btnEjecutar.setToolTipText("Ejecutar SQL");
        btnGuardar = new JButton("💾");
        btnGuardar.setToolTipText("Guardar script");
        btnAbrir = new JButton("📂");
        btnAbrir.setToolTipText("Importar script");
        
        toolBar.add(btnEjecutar);
        toolBar.add(btnGuardar);
        toolBar.add(btnAbrir);

        JPanel panelSQL = new JPanel(new BorderLayout());
        panelSQL.add(toolBar, BorderLayout.NORTH);
        panelSQL.add(scrollSQL, BorderLayout.CENTER);

        panelCentro.add(panelSQL, BorderLayout.NORTH);

        tablaResultados = new JTable();
        panelCentro.add(new JScrollPane(tablaResultados), BorderLayout.CENTER);

        add(panelCentro, BorderLayout.CENTER);

        txtOutput = new JTextArea(3, 80);
        txtOutput.setEditable(false);
        add(new JScrollPane(txtOutput), BorderLayout.SOUTH);

        btnEjecutar.addActionListener(e -> ejecutarSQL());
        btnGuardar.addActionListener(e -> guardarSQL());
        btnAbrir.addActionListener(e -> importarSQL());

        txtSQL.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "run-sql");
        txtSQL.getActionMap().put("run-sql", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ejecutarSQL();
            }
        });

        if (Global.conexion != null) {
            cargarObjetos();
        }

        setVisible(true);
    }

    private void ejecutarSQL() {
        String sql = txtSQL.getText().trim();
        if (sql.isEmpty()) {
            txtOutput.setText("⚠️ No hay script para ejecutar.");
            return;
        }
        if (Global.conexion == null) {
            txtOutput.setText("⚠️ No hay conexión activa.");
            return;
        }

        try (Statement stmt = Global.conexion.createStatement()) {
            boolean tieneRS = stmt.execute(sql);
            if (tieneRS) {
                ResultSet rs = stmt.getResultSet();
                DefaultTableModel model = new DefaultTableModel();
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    model.addColumn(meta.getColumnName(i));
                }
                while (rs.next()) {
                    Object[] fila = new Object[cols];
                    for (int i = 1; i <= cols; i++) {
                        fila[i - 1] = rs.getObject(i);
                    }
                    model.addRow(fila);
                }
                tablaResultados.setModel(model);
                txtOutput.setText("✅ Resultado devuelto con éxito.");
            } else {
                int count = stmt.getUpdateCount();
                txtOutput.setText("✅ Sentencia ejecutada. Filas afectadas: " + count);
                // refrescar objetos si cambió el esquema
                if (sql.toLowerCase().matches("^(create|drop|alter|rename|truncate).*")) {
                    cargarObjetos();
                }
            }
        } catch (Exception ex) {
            txtOutput.setText("❌ Error: " + ex.getMessage());
        }
    }

    private void cargarObjetos() {
        if (Global.conexion == null) {
            return;
        }
        try (Statement stmt = Global.conexion.createStatement()) {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Objetos BD");

            DefaultMutableTreeNode nodoTablas = new DefaultMutableTreeNode("Tablas");
            ResultSet rs = stmt.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'");
            while (rs.next()) {
                String tabla = rs.getString(1);
                DefaultMutableTreeNode nodoTabla = new DefaultMutableTreeNode(tabla);

                DefaultMutableTreeNode nodoCols = new DefaultMutableTreeNode("Columnas");
                try (ResultSet rsCols = stmt.executeQuery("SHOW COLUMNS FROM `" + tabla + "`")) {
                    while (rsCols.next()) {
                        String col = rsCols.getString("Field") + " (" + rsCols.getString("Type") + ")"
                                + ("NO".equalsIgnoreCase(rsCols.getString("Null")) ? " NOT NULL" : "");
                        nodoCols.add(new DefaultMutableTreeNode(col));
                    }
                }
                nodoTabla.add(nodoCols);

                DefaultMutableTreeNode nodoIdx = new DefaultMutableTreeNode("Índices");
                try (ResultSet rsIdx = stmt.executeQuery("SHOW INDEX FROM `" + tabla + "`")) {
                    while (rsIdx.next()) {
                        String keyName = rsIdx.getString("Key_name");
                        String colName = rsIdx.getString("Column_name");
                        boolean unique = rsIdx.getInt("Non_unique") == 0;
                        nodoIdx.add(new DefaultMutableTreeNode(
                                (unique ? "[UNIQUE] " : "") + keyName + " -> " + colName
                        ));
                    }
                }
                nodoTabla.add(nodoIdx);

                DefaultMutableTreeNode nodoTrig = new DefaultMutableTreeNode("Triggers");
                try (ResultSet rsTr = stmt.executeQuery("SHOW TRIGGERS")) {
                    while (rsTr.next()) {
                        if (tabla.equals(rsTr.getString("Table"))) {
                            nodoTrig.add(new DefaultMutableTreeNode(rsTr.getString("Trigger")));
                        }
                    }
                }
                nodoTabla.add(nodoTrig);

                nodoTablas.add(nodoTabla);
            }
            root.add(nodoTablas);

            DefaultMutableTreeNode nodoVistas = new DefaultMutableTreeNode("Vistas");
            rs = stmt.executeQuery("SHOW FULL TABLES WHERE Table_type = 'VIEW'");
            while (rs.next()) {
                nodoVistas.add(new DefaultMutableTreeNode(rs.getString(1)));
            }
            root.add(nodoVistas);

            DefaultMutableTreeNode nodoProcs = new DefaultMutableTreeNode("Procedimientos");
            rs = stmt.executeQuery("SHOW PROCEDURE STATUS WHERE Db = DATABASE()");
            while (rs.next()) {
                nodoProcs.add(new DefaultMutableTreeNode(rs.getString("Name")));
            }
            root.add(nodoProcs);

            DefaultMutableTreeNode nodoFuncs = new DefaultMutableTreeNode("Funciones");
            rs = stmt.executeQuery("SHOW FUNCTION STATUS WHERE Db = DATABASE()");
            while (rs.next()) {
                nodoFuncs.add(new DefaultMutableTreeNode(rs.getString("Name")));
            }
            root.add(nodoFuncs);

            DefaultMutableTreeNode nodoUsers = new DefaultMutableTreeNode("Usuarios");
            rs = stmt.executeQuery("SELECT user FROM mysql.user");
            while (rs.next()) {
                nodoUsers.add(new DefaultMutableTreeNode(rs.getString("user")));
            }
            root.add(nodoUsers);

            treeObjetos.setModel(new DefaultTreeModel(root));
            ((DefaultTreeModel) treeObjetos.getModel()).reload();
            treeObjetos.updateUI();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarDDL(String tipo, String nombre) {
        if (Global.conexion == null) {
            txtOutput.setText("⚠️ No hay conexión activa.");
            return;
        }
        String sql = "SHOW CREATE " + tipo + " `" + nombre + "`";
        try (Statement stmt = Global.conexion.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                String ddl = rs.getString(2); // la segunda columna contiene el código
                txtSQL.setText(ddl);
                txtOutput.setText("✅ DDL obtenido para " + tipo.toLowerCase() + " " + nombre);
            }
        } catch (Exception ex) {
            txtOutput.setText("❌ Error al obtener DDL: " + ex.getMessage());
        }
    }

    private void crearNuevaConexion() {
        JTextField txtNombre = new JTextField();
        JTextField txtHost = new JTextField("localhost");
        JTextField txtPuerto = new JTextField("3306");
        JTextField txtBase = new JTextField();
        JTextField txtUsuario = new JTextField();
        JPasswordField txtPass = new JPasswordField();

        Object[] inputs = {
            "Nombre conexión:", txtNombre,
            "Host:", txtHost,
            "Puerto:", txtPuerto,
            "Base de datos:", txtBase,
            "Usuario:", txtUsuario,
            "Contraseña:", txtPass
        };

        int result = JOptionPane.showConfirmDialog(this, inputs, "Nueva conexión", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            ConexionInfo nueva = new ConexionInfo(
                    txtNombre.getText(), txtHost.getText(), txtPuerto.getText(),
                    txtBase.getText(), txtUsuario.getText(), new String(txtPass.getPassword())
            );
            Global.conexiones.add(nueva);
            comboConexiones.addItem(nueva.toString());
            comboConexiones.setSelectedIndex(comboConexiones.getItemCount() - 1);

            Global.conectar(nueva);
            cargarObjetos();
            Global.guardarConexiones(); // <- persistir
        }
    }

    private void guardarSQL() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".sql")) {
                f = new File(f.getParentFile(), f.getName() + ".sql");
            }
            try (FileWriter fw = new FileWriter(f)) {
                fw.write(txtSQL.getText());
                txtOutput.setText("✅ Script guardado en " + f.getAbsolutePath());
            } catch (Exception ex) {
                txtOutput.setText("❌ Error al guardar: " + ex.getMessage());
            }
        }
    }

    private void importarSQL() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (Scanner sc = new Scanner(fc.getSelectedFile(), "UTF-8")) {
                StringBuilder sb = new StringBuilder();
                while (sc.hasNextLine()) {
                    sb.append(sc.nextLine()).append("\n");
                }
                txtSQL.setText(sb.toString());
                txtOutput.setText("✅ Script cargado desde " + fc.getSelectedFile().getAbsolutePath());
            } catch (Exception ex) {
                txtOutput.setText("❌ Error al abrir: " + ex.getMessage());
            }
        }
    }

    private int indexOfByNombre(String nombre) {
        for (int i = 0; i < Global.conexiones.size(); i++) {
            if (nombre != null && nombre.equals(Global.conexiones.get(i).nombre)) {
                return i;
            }
        }
        return -1;
    }
}
