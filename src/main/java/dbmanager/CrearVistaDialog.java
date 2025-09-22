package dbmanager;

import javax.swing.*;
import java.awt.*;
import java.sql.Statement;
/**
 *
 * @author elean
 */
public class CrearVistaDialog extends JDialog {
    private JTextField txtNombreVista;
    private JTextArea txtQuery;

    public CrearVistaDialog(JFrame parent) {
        super(parent, "Crear Vista", true);
        setSize(400, 300);
        setLayout(new BorderLayout());

        JPanel panelTop = new JPanel(new GridLayout(1, 2));
        panelTop.add(new JLabel("Nombre vista:"));
        txtNombreVista = new JTextField();
        panelTop.add(txtNombreVista);

        add(panelTop, BorderLayout.NORTH);

        txtQuery = new JTextArea("SELECT * FROM ...");
        add(new JScrollPane(txtQuery), BorderLayout.CENTER);

        JButton btnCrear = new JButton("Crear Vista");
        btnCrear.addActionListener(e -> crearVista());
        add(btnCrear, BorderLayout.SOUTH);
    }

    private void crearVista() {
        String nombre = txtNombreVista.getText().trim();
        String query = txtQuery.getText().trim();

        if (nombre.isEmpty() || query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debe ingresar nombre y query.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sql = "CREATE VIEW " + nombre + " AS " + query;
        try (Statement st = Global.conexion.createStatement()) {
            st.executeUpdate(sql);
            JOptionPane.showMessageDialog(this, "✅ Vista creada.");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "❌ Error: " + ex.getMessage());
        }
    }
}
