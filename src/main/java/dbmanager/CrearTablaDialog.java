package dbmanager;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
/**
 *
 * @author elean
 */
public class CrearTablaDialog extends JDialog {

    private JTextField txtNombreTabla;
    private DefaultListModel<String> modeloColumnas;
    private JList<String> listaColumnas;

    public CrearTablaDialog(JFrame parent) {
        super(parent, "Crear Tabla", true);
        setSize(400, 300);
        setLayout(new BorderLayout());

        JPanel panelTop = new JPanel(new GridLayout(2, 2));
        panelTop.add(new JLabel("Nombre tabla:"));
        txtNombreTabla = new JTextField();
        panelTop.add(txtNombreTabla);

        add(panelTop, BorderLayout.NORTH);

        modeloColumnas = new DefaultListModel<>();
        listaColumnas = new JList<>(modeloColumnas);
        add(new JScrollPane(listaColumnas), BorderLayout.CENTER);

        // Botones para columnas
        JPanel panelCols = new JPanel();
        JButton btnAddCol = new JButton("➕ Columna");
        JButton btnDelCol = new JButton("❌ Eliminar");

        btnAddCol.addActionListener(e -> agregarColumna());
        btnDelCol.addActionListener(e -> {
            int sel = listaColumnas.getSelectedIndex();
            if (sel >= 0) modeloColumnas.remove(sel);
        });

        panelCols.add(btnAddCol);
        panelCols.add(btnDelCol);
        add(panelCols, BorderLayout.SOUTH);

        // Botón crear
        JButton btnCrear = new JButton("Crear Tabla");
        btnCrear.addActionListener(e -> crearTabla());
        add(btnCrear, BorderLayout.EAST);
    }

    private void agregarColumna() {
        JTextField txtNombre = new JTextField();
        String[] tipos = {"INT", "VARCHAR(255)", "DATE", "DOUBLE"};
        JComboBox<String> cbTipo = new JComboBox<>(tipos);
        JCheckBox chkPK = new JCheckBox("PRIMARY KEY");
        JCheckBox chkNN = new JCheckBox("NOT NULL");

        Object[] inputs = {
            "Nombre columna:", txtNombre,
            "Tipo:", cbTipo,
            chkPK, chkNN
        };

        int res = JOptionPane.showConfirmDialog(this, inputs, "Nueva Columna", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String colDef = txtNombre.getText() + " " + cbTipo.getSelectedItem();
            if (chkNN.isSelected()) colDef += " NOT NULL";
            if (chkPK.isSelected()) colDef += " PRIMARY KEY";
            modeloColumnas.addElement(colDef);
        }
    }

    private void crearTabla() {
        String nombreTabla = txtNombreTabla.getText().trim();
        if (nombreTabla.isEmpty() || modeloColumnas.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debe ingresar nombre y columnas.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> cols = new ArrayList<>();
        for (int i = 0; i < modeloColumnas.size(); i++) {
            cols.add(modeloColumnas.get(i));
        }

        String sql = "CREATE TABLE " + nombreTabla + " (\n" + String.join(",\n", cols) + "\n)";
        try (Statement st = Global.conexion.createStatement()) {
            st.executeUpdate(sql);
            JOptionPane.showMessageDialog(this, "✅ Tabla creada.");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "❌ Error: " + ex.getMessage());
        }
    }
}
