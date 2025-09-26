package dbmanager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Scanner;

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
    private JCheckBox chkReplicarPg;
    private Map<String, List<String>> pkCache = new HashMap<>();

    private static final Color BG_APP = hex("#E6E8ED");
    private static final Color BG_PANEL = hex("#E6E8ED");
    private static final Color FG_TEXT = hex("#111111");
    private static final Color BTN_BG = hex("#F8BBD0");
    private static final Color BTN_BG_HOVER = hex("#F48FB1");
    private static final Color BTN_BORDER = hex("#E1A5BA");
    private static final Color FIELD_BG = Color.WHITE;
    private static final Color SELECTION_BG = hex("#F48FB1");
    private static final Color TABLE_GRID = hex("#D3D7DE");
    private static final Color TOOLBAR_BG = hex("#EDEFF3");

    public MainFrame() {
        installLookAndFeelAndTheme();

        super.setTitle("Mini-DBManager");
        setLayout(new BorderLayout());
        setSize(1000, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_APP);
        ((JComponent) getContentPane()).setOpaque(true);
        getContentPane().setBackground(BG_APP);
        JPanel panelTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stylePanel(panelTop);
        JLabel lblConn = new JLabel("Conexión:");
        styleLabel(lblConn);

        comboConexiones = new JComboBox<>();
        styleCombo(comboConexiones);

        panelTop.add(lblConn);
        panelTop.add(comboConexiones);

        JButton btnNuevaConexion = new JButton("Nueva conexión");
        styleButton(btnNuevaConexion);
        btnNuevaConexion.addActionListener(e -> crearNuevaConexion());
        panelTop.add(btnNuevaConexion);

        JButton btnDiagrama = new JButton("📊 Ver Diagrama Entidad-Relacion");
        styleButton(btnDiagrama);
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

        add(panelTop, BorderLayout.NORTH);

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
                pkCache.clear();
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
        treeObjetos.setForeground(FG_TEXT);
        treeObjetos.setBackground(BG_PANEL);
        treeObjetos.setRowHeight(22);
        treeObjetos.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane scrollTree = new JScrollPane(treeObjetos);
        styleScroll(scrollTree);
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
                    stylePopup(menu);

                    if ("Tablas".equals(nombreNodo)) {
                        JMenuItem crearTabla = new JMenuItem("➕ Crear Tabla");
                        styleMenuItem(crearTabla);
                        crearTabla.addActionListener(ev -> {
                            CrearTablaDialog dlg = new CrearTablaDialog(MainFrame.this);
                            dlg.setVisible(true);
                            cargarObjetos();
                        });
                        menu.add(crearTabla);
                    } else if ("Vistas".equals(nombreNodo)) {
                        JMenuItem crearVista = new JMenuItem("➕ Crear Vista");
                        styleMenuItem(crearVista);
                        crearVista.addActionListener(ev -> {
                            CrearVistaDialog dlg = new CrearVistaDialog(MainFrame.this);
                            dlg.setVisible(true);
                            cargarObjetos();
                        });
                        menu.add(crearVista);
                    }

                    if (padre != null) {
                        String categoria = padre.toString();
                        if ("Tablas".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de tabla");
                            styleMenuItem(verDDL);
                            verDDL.addActionListener(ev -> mostrarDDL("TABLE", nombreNodo));
                            menu.add(verDDL);

                            JMenuItem diagramaTabla = new JMenuItem("📊 Ver Diagrama Relacional");
                            styleMenuItem(diagramaTabla);
                            diagramaTabla.addActionListener(ev
                                    -> new DiagramaER(Global.conexionActual.baseDatos, nombreNodo, "TABLE"));
                            menu.add(diagramaTabla);
                        } else if ("Vistas".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de vista");
                            styleMenuItem(verDDL);
                            verDDL.addActionListener(ev -> mostrarDDL("VIEW", nombreNodo));
                            menu.add(verDDL);

                            JMenuItem diagramaVista = new JMenuItem("📊 Ver Diagrama Relacional");
                            styleMenuItem(diagramaVista);
                            diagramaVista.addActionListener(ev
                                    -> new DiagramaER(Global.conexionActual.baseDatos, nombreNodo, "VIEW"));
                            menu.add(diagramaVista);
                        } else if ("Procedimientos".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de procedimiento");
                            styleMenuItem(verDDL);
                            verDDL.addActionListener(ev -> mostrarDDL("PROCEDURE", nombreNodo));
                            menu.add(verDDL);
                        } else if ("Funciones".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de función");
                            styleMenuItem(verDDL);
                            verDDL.addActionListener(ev -> mostrarDDL("FUNCTION", nombreNodo));
                            menu.add(verDDL);
                        } else if ("Triggers".equals(categoria)) {
                            JMenuItem verDDL = new JMenuItem("📜 Ver DDL de trigger");
                            styleMenuItem(verDDL);
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
        stylePanel(panelCentro);

        txtSQL = new JTextArea(15, 40);
        styleTextArea(txtSQL);
        JScrollPane scrollSQL = new JScrollPane(txtSQL);
        scrollSQL.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TABLE_GRID),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        styleScroll(scrollSQL);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(TOOLBAR_BG);
        toolBar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        btnEjecutar = new JButton("Ejecutar");
        btnGuardar = new JButton("Guardar");
        btnAbrir = new JButton("Importar");
        styleButton(btnEjecutar);
        styleButton(btnGuardar);
        styleButton(btnAbrir);

        JButton btnConectarPg = new JButton("Conexión Postgres");
        styleButton(btnConectarPg);
        btnConectarPg.setToolTipText("Conectar a PostgreSQL");
        btnConectarPg.addActionListener(e -> conectarAPostgres());

        chkReplicarPg = new JCheckBox("Replicar a PostgreSQL");
        styleCheck(chkReplicarPg);

        toolBar.add(btnEjecutar);
        toolBar.add(btnGuardar);
        toolBar.add(btnAbrir);
        toolBar.addSeparator();
        toolBar.add(btnConectarPg);
        toolBar.add(chkReplicarPg);

        JPanel panelSQL = new JPanel(new BorderLayout());
        stylePanel(panelSQL);
        panelSQL.add(toolBar, BorderLayout.NORTH);
        panelSQL.add(scrollSQL, BorderLayout.CENTER);

        panelCentro.add(panelSQL, BorderLayout.NORTH);

        tablaResultados = new JTable();
        styleTable(tablaResultados);
        JScrollPane scrollTable = new JScrollPane(tablaResultados);
        styleScroll(scrollTable);
        panelCentro.add(scrollTable, BorderLayout.CENTER);

        add(panelCentro, BorderLayout.CENTER);
        txtOutput = new JTextArea(3, 80);
        styleTextArea(txtOutput);
        txtOutput.setEditable(false);
        JScrollPane scrollOut = new JScrollPane(txtOutput);
        scrollOut.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TABLE_GRID),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        styleScroll(scrollOut);
        add(scrollOut, BorderLayout.SOUTH);
        btnEjecutar.addActionListener(e -> ejecutarSQL());
        btnGuardar.addActionListener(e -> guardarSQL());
        btnAbrir.addActionListener(e -> importarSQL());

        txtSQL.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "run-sql"
        );
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
            txtOutput.setText("No hay script para ejecutar.");
            return;
        }
        if (Global.conexion == null) {
            txtOutput.setText("No hay conexión activa.");
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
                txtOutput.setText("Resultado devuelto con éxito.");
            } else {
                int count = stmt.getUpdateCount();
                txtOutput.setText("Sentencia ejecutada. Filas afectadas: " + count);
                if (sql.toLowerCase().matches("^(create|drop|alter|rename|truncate).*")) {
                    cargarObjetos();
                }
                replicarAPostgres(sql);
            }
        } catch (Exception ex) {
            txtOutput.setText("Error: " + ex.getMessage());
        }
    }

    private void replicarAPostgres(String sql) {
        if (GlobalPg.conexionPg == null || !chkReplicarPg.isSelected()) {
            return;
        }

        try (Statement pg = GlobalPg.conexionPg.createStatement()) {
            String sqlPg = sql;
            if (SqlTranslator.isDDL(sql)) {
                String s = sql.trim().toLowerCase(java.util.Locale.ROOT);
                if (s.startsWith("create table")) {
                    sqlPg = SqlTranslator.translateCreateTableToPg(sql);
                } else if (s.startsWith("create view") || s.startsWith("create or replace view")) {
                    sqlPg = SqlTranslator.translateCreateViewToPg(sql);
                } else if (s.startsWith("drop view")) {
                    sqlPg = SqlTranslator.quoteFix(sql);
                } else if (s.startsWith("alter table") || s.startsWith("drop ")
                        || s.startsWith("truncate ") || s.startsWith("rename ")
                        || s.startsWith("create index") || s.startsWith("create unique index")) {
                    sqlPg = SqlTranslator.quoteFix(sql);
                }
                pg.execute(sqlPg);
                txtOutput.append("\nPostgreSQL: DDL replicado.");
            } else if (SqlTranslator.isDML(sql)) {
                String s = sql.trim().toLowerCase(java.util.Locale.ROOT);
                if (s.startsWith("insert")) {
                    String table = SqlTranslator.tableNameFromInsert(sql);
                    List<String> pkCols = (table != null) ? getPkCols(table) : Collections.emptyList();
                    sqlPg = SqlTranslator.translateInsertToPgUpsert(sql, pkCols);
                    pg.executeUpdate(sqlPg);
                } else {
                    pg.executeUpdate(SqlTranslator.quoteFix(sql));
                }
                txtOutput.append("\nPostgreSQL: DML replicado.");
            }
        } catch (Exception ex) {
            txtOutput.append("\nReplicación PG falló: " + ex.getMessage());
        }
    }

    private List<String> getPkCols(String table) {
        List<String> cached = pkCache.get(table);
        if (cached != null) {
            return cached;
        }
        List<String> pks = new ArrayList<>();
        try (Statement st = Global.conexion.createStatement(); ResultSet rs = st.executeQuery("SHOW KEYS FROM `" + table + "` WHERE Key_name='PRIMARY'")) {
            while (rs.next()) {
                pks.add(rs.getString("Column_name"));
            }
        } catch (Exception ignored) {
        }
        pkCache.put(table, pks);
        return pks;
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
            txtOutput.setText("No hay conexión activa.");
            return;
        }
        String sql = "SHOW CREATE " + tipo + " `" + nombre + "`";
        try (Statement stmt = Global.conexion.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String ddl = rs.getString(2);
                txtSQL.setText(ddl);
                txtOutput.setText("DDL obtenido para " + tipo.toLowerCase() + " " + nombre);
            }
        } catch (Exception ex) {
            txtOutput.setText("Error al obtener DDL: " + ex.getMessage());
        }
    }

    private void crearNuevaConexion() {
        JTextField txtNombre = styledField(new JTextField());
        JTextField txtHost = styledField(new JTextField("localhost"));
        JTextField txtPuerto = styledField(new JTextField("3306"));
        JTextField txtBase = styledField(new JTextField());
        JTextField txtUsuario = styledField(new JTextField());
        JPasswordField txtPass = (JPasswordField) styledField(new JPasswordField());

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
            pkCache.clear();
            cargarObjetos();
            Global.guardarConexiones();
        }
    }

    private void conectarAPostgres() {
        JTextField txtHost = styledField(new JTextField("localhost"));
        JTextField txtPuerto = styledField(new JTextField("5432"));
        JTextField txtBase = styledField(new JTextField());
        JTextField txtUsuario = styledField(new JTextField());
        JPasswordField txtPass = (JPasswordField) styledField(new JPasswordField());

        Object[] inputs = {
            "Host:", txtHost,
            "Puerto:", txtPuerto,
            "Base:", txtBase,
            "Usuario:", txtUsuario,
            "Contraseña:", txtPass
        };

        int result = JOptionPane.showConfirmDialog(this, inputs, "Conectar a PostgreSQL", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            ConexionInfo info = new ConexionInfo("PG",
                    txtHost.getText(), txtPuerto.getText(), txtBase.getText(),
                    txtUsuario.getText(), new String(txtPass.getPassword()));
            GlobalPg.conectar(info);
            if (GlobalPg.conexionPg != null) {
                txtOutput.setText("✅ Conectado a PostgreSQL: " + info.host + ":" + info.puerto + "/" + info.baseDatos);
            } else {
                txtOutput.setText("❌ No se pudo conectar a PostgreSQL");
            }
        }
    }

    private void guardarSQL() {
        JFileChooser fc = new JFileChooser();
        styleFileChooser(fc);
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
        styleFileChooser(fc);
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

    private void installLookAndFeelAndTheme() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        Font uiFont = pickFont(new String[]{"Segoe UI", "Inter", "Nunito", "SansSerif"}, Font.PLAIN, 13);
        setUIFont(uiFont);
        UIManager.put("control", BG_PANEL);
        UIManager.put("info", BG_PANEL);
        UIManager.put("nimbusLightBackground", BG_PANEL);
        UIManager.put("text", FG_TEXT);
        UIManager.put("menuText", FG_TEXT);
        UIManager.put("Button.background", BTN_BG);
        UIManager.put("Button.foreground", FG_TEXT);
        UIManager.put("ToolTip.background", Color.WHITE);
        UIManager.put("ToolTip.foreground", FG_TEXT);
        UIManager.put("Table.gridColor", TABLE_GRID);
        UIManager.put("Table.background", Color.WHITE);
        UIManager.put("Table.foreground", FG_TEXT);
        UIManager.put("Table.selectionBackground", SELECTION_BG);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TextField.background", FIELD_BG);
        UIManager.put("TextArea.background", FIELD_BG);
        UIManager.put("PasswordField.background", FIELD_BG);
        UIManager.put("ComboBox.background", FIELD_BG);
    }

    private static void setUIFont(Font f) {
        java.util.Enumeration<?> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof Font) {
                UIManager.put(key, f);
            }
        }
    }

    private static Font pickFont(String[] names, int style, int size) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Set<String> available = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
        for (String n : names) {
            if (available.contains(n)) {
                return new Font(n, style, size);
            }
        }
        return new Font("Inter", style, size);
    }

    private static void stylePanel(JComponent p) {
        p.setBackground(BG_PANEL);
        p.setForeground(FG_TEXT);
    }

    private static void styleLabel(JLabel l) {
        l.setForeground(FG_TEXT);
    }

    private static void styleButton(AbstractButton b) {
        b.setBackground(BTN_BG);
        b.setForeground(FG_TEXT);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BTN_BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                b.setBackground(BTN_BG_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                b.setBackground(BTN_BG);
            }
        });
    }

    private static void styleCheck(JCheckBox c) {
        c.setForeground(FG_TEXT);
        c.setBackground(BG_PANEL);
        c.setFocusPainted(false);
    }

    private static void styleCombo(JComboBox<?> c) {
        c.setBackground(FIELD_BG);
        c.setForeground(FG_TEXT);
        c.setBorder(BorderFactory.createLineBorder(TABLE_GRID));
    }

    private static void styleTextArea(JTextArea t) {
        t.setBackground(FIELD_BG);
        t.setForeground(FG_TEXT);
        t.setCaretColor(FG_TEXT);
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TABLE_GRID),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
    }

    private static void styleTable(JTable t) {
        t.setBackground(Color.WHITE);
        t.setForeground(FG_TEXT);
        t.setGridColor(TABLE_GRID);
        t.setRowHeight(24);
        t.setSelectionBackground(SELECTION_BG);
        t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setBackground(BG_PANEL);
        t.getTableHeader().setForeground(FG_TEXT);
        t.getTableHeader().setBorder(BorderFactory.createLineBorder(TABLE_GRID));
    }

    private static void styleScroll(JScrollPane sp) {
        sp.getViewport().setBackground(BG_PANEL);
        sp.setBorder(BorderFactory.createEmptyBorder());
    }

    private static void stylePopup(JPopupMenu m) {
        m.setBackground(Color.WHITE);
        m.setBorder(BorderFactory.createLineBorder(TABLE_GRID));
    }

    private static void styleMenuItem(JMenuItem mi) {
        mi.setBackground(Color.WHITE);
        mi.setForeground(FG_TEXT);
    }

    private static JTextField styledField(JTextField f) {
        f.setBackground(FIELD_BG);
        f.setForeground(FG_TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TABLE_GRID),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        return f;
    }

    private static Color hex(String s) {
        return new Color(
                Integer.valueOf(s.substring(1, 3), 16),
                Integer.valueOf(s.substring(3, 5), 16),
                Integer.valueOf(s.substring(5, 7), 16)
        );
    }

    private void styleFileChooser(JFileChooser fc) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
