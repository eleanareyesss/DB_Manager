package dbmanager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author elean
 */
public class DiagramaER extends JFrame {

    static class Col {

        String name;
        String type;
        boolean pk;
        boolean fk;
    }

    static class FK {

        String table;
        List<String> cols = new ArrayList<>();
        String refTable;
        List<String> refCols = new ArrayList<>();
        String onDelete;
        String onUpdate;
    }

    static class Table {

        String name;
        List<Col> cols = new ArrayList<>();
        List<String> pkCols = new ArrayList<>();
        List<FK> fks = new ArrayList<>();
    }

    private final Map<String, Table> model = new LinkedHashMap<>();
    private final String singleTableNameOrNull;
    private final boolean individual;

    public DiagramaER(String baseDatos) {
        this(baseDatos, null, null);
    }

    public DiagramaER(String baseDatos, String objeto, String tipo) {
        boolean isTable = (objeto != null && "TABLE".equalsIgnoreCase(tipo));
        this.individual = isTable;
        this.singleTableNameOrNull = isTable ? objeto : null;

        setTitle("Diagrama ER - " + baseDatos + (individual ? " - " + objeto : ""));
        setSize(1100, 800);
        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);

        cargarModelo();

        DiagramPanel panel = new DiagramPanel();
        JScrollPane sp = new JScrollPane(panel);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        sp.getHorizontalScrollBar().setUnitIncrement(24);

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        JButton bOut = new JButton(" − ");
        JButton bFit = new JButton(" Ajustar ");
        JButton bIn = new JButton(" + ");
        bOut.addActionListener(e -> panel.zoomOutCenter());
        bFit.addActionListener(e -> panel.zoomResetAndFitInViewport());
        bIn.addActionListener(e -> panel.zoomInCenter());
        tb.add(bOut);
        tb.add(bFit);
        tb.add(bIn);

        add(tb, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
        setVisible(true);
        SwingUtilities.invokeLater(panel::zoomResetAndFitInViewport);
    }
    private void cargarModelo() {
        if (Global.conexion == null) {
            JOptionPane.showMessageDialog(this, "No hay conexión activa.");
            return;
        }

        List<String> allTables = new ArrayList<>();
        try (Statement st = Global.conexion.createStatement(); ResultSet rs = st.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")) {
            while (rs.next()) {
                String t = rs.getString(1);
                allTables.add(t);
                Table tab = new Table();
                tab.name = t;
                model.put(t, tab);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        for (String t : allTables) {
            Table tab = model.get(t);
            try (Statement st = Global.conexion.createStatement(); ResultSet rs = st.executeQuery("SHOW COLUMNS FROM `" + t + "`")) {
                while (rs.next()) {
                    Col c = new Col();
                    c.name = rs.getString("Field");
                    c.type = rs.getString("Type");
                    tab.cols.add(c);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for (String t : allTables) {
            Table tab = model.get(t);
            try (Statement st = Global.conexion.createStatement(); ResultSet rs = st.executeQuery("SHOW KEYS FROM `" + t + "` WHERE Key_name='PRIMARY'")) {
                while (rs.next()) {
                    tab.pkCols.add(rs.getString("Column_name"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (Col c : tab.cols) {
                if (tab.pkCols.contains(c.name)) {
                    c.pk = true;
                }
            }
        }

        for (String t : allTables) {
            Table tab = model.get(t);
            String ddl = null;
            try (Statement st = Global.conexion.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + t + "`")) {
                if (rs.next()) {
                    ddl = rs.getString(2);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (ddl == null) {
                continue;
            }

            parseFKsFromDDL(t, ddl, tab.fks);
            for (FK fk : tab.fks) {
                for (String c : fk.cols) {
                    for (Col col : tab.cols) {
                        if (col.name.equalsIgnoreCase(c)) {
                            col.fk = true;
                        }
                    }
                }
            }
        }

        if (individual && singleTableNameOrNull != null) {
            Set<String> keep = new LinkedHashSet<>();
            keep.add(singleTableNameOrNull);

            Table base = model.get(singleTableNameOrNull);
            if (base != null) {
                for (FK fk : base.fks) {
                    keep.add(fk.refTable);
                }
            }

            for (Table t : model.values()) {
                for (FK fk : t.fks) {
                    if (singleTableNameOrNull.equalsIgnoreCase(fk.refTable)) {
                        keep.add(t.name);
                    }
                }
            }

            model.entrySet().removeIf(e -> !keep.contains(e.getKey()));
        }
    }

    private static final Pattern FK_PATTERN = Pattern.compile(
            "(?is)CONSTRAINT\\s+`[^`]+`\\s+FOREIGN\\s+KEY\\s*\\(([^)]+)\\)\\s*REFERENCES\\s+`([^`]+)`\\s*\\(([^)]+)\\)"
            + "(?:\\s*ON\\s+DELETE\\s+(RESTRICT|CASCADE|SET\\s+NULL|NO\\s+ACTION))?"
            + "(?:\\s*ON\\s+UPDATE\\s+(RESTRICT|CASCADE|SET\\s+NULL|NO\\s+ACTION))?"
    );

    private void parseFKsFromDDL(String tableName, String ddl, List<FK> out) {
        String norm = ddl.replace("\r", " ").replace("\n", " ");
        Matcher m = FK_PATTERN.matcher(norm);
        while (m.find()) {
            FK fk = new FK();
            fk.table = tableName;
            fk.refTable = m.group(2);
            fk.onDelete = opt(m.group(4));
            fk.onUpdate = opt(m.group(5));
            for (String s : splitColsList(m.group(1))) {
                fk.cols.add(s);
            }
            for (String s : splitColsList(m.group(3))) {
                fk.refCols.add(s);
            }
            out.add(fk);
        }
    }

    private String opt(String s) {
        return (s == null) ? null : s.replaceAll("\\s+", " ");
    }

    private List<String> splitColsList(String s) {
        String[] parts = s.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String c = p.replace("`", "").trim();
            if (!c.isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    private class DiagramPanel extends JPanel implements Scrollable {

        private final Map<String, Rectangle> nodeBounds = new LinkedHashMap<>();
        private final int padX = 14, padY = 10;
        private final int headerH = 24, rowH = 18;
        private final int gapX = 60, gapY = 50;
        private final int minW = 180;
        private double zoom = 1.0;
        private int panX = 0, panY = 0;
        private Point dragAnchor = null;
        private Rectangle contentBoundsLogical = new Rectangle(0, 0, 1200, 800);

        DiagramPanel() {
            setBackground(Color.WHITE);
            setOpaque(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragAnchor = e.getPoint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragAnchor = null;
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragAnchor != null) {
                        panX += e.getX() - dragAnchor.x;
                        panY += e.getY() - dragAnchor.y;
                        dragAnchor = e.getPoint();
                        repaint();
                    }
                }
            });
            addMouseWheelListener(e -> {
                if (!e.isControlDown()) {
                    return;
                }
                double factor = (e.getWheelRotation() < 0) ? 1.1 : 1 / 1.1;
                zoomAtPoint(factor, e.getPoint());
            });
        }

        void zoomResetAndFitInViewport() {
            JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (vp == null) {
                zoom = 1.0;
                panX = panY = 0;
                repaint();
                return;
            }

            Dimension view = vp.getExtentSize();
            int margin = 40;
            double sx = (view.width - margin) / (double) contentBoundsLogical.width;
            double sy = (view.height - margin) / (double) contentBoundsLogical.height;
            zoom = Math.max(0.1, Math.min(sx, sy));
            panX = (int) ((view.width - contentBoundsLogical.width * zoom) / 2);
            panY = (int) ((view.height - contentBoundsLogical.height * zoom) / 2);
            revalidate();
            repaint();
        }

        void zoomInCenter() {
            zoomAtPoint(1.1, getCenter());
        }

        void zoomOutCenter() {
            zoomAtPoint(1 / 1.1, getCenter());
        }

        private Point getCenter() {
            Rectangle r = getVisibleRect();
            return new Point(r.x + r.width / 2, r.y + r.height / 2);
        }

        private void zoomAtPoint(double factor, Point pivotView) {
            double old = zoom;
            zoom = clamp(zoom * factor, 0.2, 4.0);
            double s = zoom / old;
            panX = (int) (pivotView.x - s * (pivotView.x - panX));
            panY = (int) (pivotView.y - s * (pivotView.y - panY));
            revalidate();
            repaint();
        }

        private double clamp(double v, double a, double b) {
            return Math.max(a, Math.min(b, v));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(panX, panY);
            g2.scale(zoom, zoom);

            nodeBounds.clear();
            List<Table> tables = new ArrayList<>(model.values());
            Font titleFont = g2.getFont().deriveFont(Font.BOLD, 12f);
            Font colFont = g2.getFont().deriveFont(Font.PLAIN, 12f);

            int maxColsPerRow = Math.max(1, (int) Math.ceil(Math.sqrt(tables.size())));
            int curX = 40, curY = 40, colCount = 0, rowMaxH = 0;

            for (Table t : tables) {
                Dimension sz = measureNode(g2, titleFont, colFont, t);

                if (colCount >= maxColsPerRow) {
                    curX = 40;
                    curY += rowMaxH + gapY;
                    colCount = 0;
                    rowMaxH = 0;
                }

                Rectangle r = new Rectangle(curX, curY, sz.width, sz.height);
                nodeBounds.put(t.name, r);

                curX += sz.width + gapX;
                colCount++;
                rowMaxH = Math.max(rowMaxH, sz.height);
            }

            Rectangle union = null;
            for (Rectangle r : nodeBounds.values()) {
                union = (union == null) ? new Rectangle(r) : union.union(r);
            }
            if (union == null) {
                union = new Rectangle(0, 0, 1200, 800);
            }
            contentBoundsLogical = new Rectangle(0, 0, union.x + union.width + 40, union.y + union.height + 40);

            for (Table t : tables) {
                Rectangle from = nodeBounds.get(t.name);
                if (from == null) {
                    continue;
                }
                for (FK fk : t.fks) {
                    Rectangle to = nodeBounds.get(fk.refTable);
                    if (to == null) {
                        continue;
                    }
                    drawConnector(g2, from, to, fk);
                }
            }

            for (Table t : tables) {
                drawTableNode(g2, nodeBounds.get(t.name), titleFont, colFont, t);
            }

            g2.dispose();
            Dimension newPref = new Dimension(
                    (int) (contentBoundsLogical.width * zoom) + Math.abs(panX) + 200,
                    (int) (contentBoundsLogical.height * zoom) + Math.abs(panY) + 200
            );
            if (!newPref.equals(getPreferredSize())) {
                setPreferredSize(newPref);
                revalidate();
            }
        }

        private Dimension measureNode(Graphics2D g2, Font titleFont, Font colFont, Table t) {
            FontMetrics ft = g2.getFontMetrics(titleFont);
            FontMetrics fc = g2.getFontMetrics(colFont);
            int w = Math.max(minW, ft.stringWidth(t.name) + 2 * padX);
            int h = headerH + padY;
            for (Col c : t.cols) {
                String tag = (c.pk ? "[PK] " : "") + (c.fk ? "[FK] " : "");
                String line = tag + c.name + " : " + c.type;
                w = Math.max(w, fc.stringWidth(line) + 2 * padX);
                h += rowH;
            }
            h += padY;
            return new Dimension(w, h);
        }

        private void drawTableNode(Graphics2D g2, Rectangle r, Font titleFont, Font colFont, Table t) {
            g2.setColor(new Color(223, 236, 250));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
            g2.setColor(new Color(120, 155, 200));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 12, 12);

            g2.setColor(new Color(180, 205, 235));
            g2.fillRoundRect(r.x, r.y, r.width, headerH + 6, 12, 12);
            g2.setColor(new Color(120, 155, 200));
            g2.drawRoundRect(r.x, r.y, r.width, headerH + 6, 12, 12);

            g2.setFont(titleFont);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(t.name, r.x + padX, r.y + headerH - 6);

            g2.setFont(colFont);
            g2.setColor(Color.BLACK);
            int y = r.y + headerH + padY - 6;
            for (Col c : t.cols) {
                String tag = (c.pk ? "[PK] " : "") + (c.fk ? "[FK] " : "");
                String line = tag + c.name + " : " + c.type;
                g2.drawString(line, r.x + padX, y);
                y += rowH;
            }
        }

        private void drawConnector(Graphics2D g2, Rectangle from, Rectangle to, FK fk) {
            int x1 = from.x + from.width;
            int y1 = from.y + from.height / 2;
            int x2 = to.x;
            int y2 = to.y + to.height / 2;
            int midX = (x1 + x2) / 2;

            g2.setStroke(new BasicStroke(1.8f));
            g2.setColor(new Color(200, 70, 70));

            Path2D path = new Path2D.Double();
            path.moveTo(x1, y1);
            path.lineTo(midX, y1);
            path.lineTo(midX, y2);
            path.lineTo(x2, y2);
            g2.draw(path);

            drawArrow(g2, x2, y2, x2 - 12, y2);
            String label = fk.cols.size() == 1
                    ? fk.cols.get(0) + " → " + (fk.refCols.isEmpty() ? fk.refTable : fk.refCols.get(0))
                    : String.join(",", fk.cols) + " → " + (fk.refCols.isEmpty() ? fk.refTable : String.join(",", fk.refCols));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            g2.setColor(new Color(100, 100, 100));
            g2.drawString(label, midX + 4, (y1 + y2) / 2 - 4);
        }

        private void drawArrow(Graphics2D g2, int xTip, int yTip, int xTail, int yTail) {
            double dx = xTip - xTail, dy = yTip - yTail;
            double len = Math.hypot(dx, dy);
            if (len == 0) {
                return;
            }
            double ux = dx / len, uy = dy / len;

            int L = 10, W = 5;
            int x1 = xTip, y1 = yTip;
            int x2 = (int) (xTip - L * ux + W * uy);
            int y2 = (int) (yTip - L * uy - W * ux);
            int x3 = (int) (xTip - L * ux - W * uy);
            int y3 = (int) (yTip - L * uy + W * ux);
            g2.fillPolygon(new int[]{x1, x2, x3}, new int[]{y1, y2, y3}, 3);
        }

        /* ==== Scrollable ==== */
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return 120;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1200, 800);
        }
    }
}