// AttendEase.java
// Simple Attendance Management desktop app using Swing + SQLite (single-file).
// Author: ChatGPT (example)
// Notes: Requires sqlite-jdbc on classpath (org.xerial)

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class AttendEase extends JFrame {
    // DB file
    private static final String DB_FILE = "attendease.db";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Database db;

    // UI components
    private DefaultListModel<Student> studentListModel = new DefaultListModel<>();
    private JList<Student> studentJList = new JList<>(studentListModel);

    private JTable attendanceTable;
    private DefaultTableModel attendanceTableModel;

    private JTable percentTable;
    private DefaultTableModel percentTableModel;

    private JSpinner dateSpinner;

    private JTextField searchField;

    public AttendEase(Database db) {
        this.db = db;
        setTitle("AttendEase — Attendance Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        initUI();
        loadStudents();
        updateAttendanceTable();
        updatePercentageTable();
    }

    private void initUI() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(12,12,12,12));
        JLabel title = new JLabel("AttendEase");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchField = new JTextField(18);
        searchField.setToolTipText("Search students by name or roll");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { filterStudents(); }
            public void removeUpdate(DocumentEvent e) { filterStudents(); }
            public void insertUpdate(DocumentEvent e) { filterStudents(); }
        });
        topRight.add(searchField);
        header.add(topRight, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Left panel - students management
        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(260, 0));
        left.setBorder(new EmptyBorder(8,8,8,8));
        left.add(new JLabel("Students"), BorderLayout.NORTH);

        studentJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(studentJList);
        left.add(listScroll, BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new GridLayout(0,1,6,6));
        JButton addStudentBtn = new JButton("Add Student");
        addStudentBtn.addActionListener(e -> showAddStudentDialog());
        JButton editStudentBtn = new JButton("Edit Selected");
        editStudentBtn.addActionListener(e -> editSelectedStudent());
        JButton removeStudentBtn = new JButton("Remove Selected");
        removeStudentBtn.addActionListener(e -> removeSelectedStudent());
        leftButtons.add(addStudentBtn);
        leftButtons.add(editStudentBtn);
        leftButtons.add(removeStudentBtn);

        JButton exportStudentsBtn = new JButton("Export Students CSV");
        exportStudentsBtn.addActionListener(e -> exportStudentsCsv());
        leftButtons.add(exportStudentsBtn);

        left.add(leftButtons, BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);

        // Center - Attendance and Charts
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new EmptyBorder(8,8,8,8));

        // Top area: date picker and quick actions
        JPanel topControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        // date spinner
        SpinnerDateModel sd = new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH);
        dateSpinner = new JSpinner(sd);
        JSpinner.DateEditor de = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(de);
        dateSpinner.addChangeListener(e -> updateAttendanceTable());
        topControls.add(new JLabel("Date:"));
        topControls.add(dateSpinner);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> { updateAttendanceTable(); updatePercentageTable(); });
        topControls.add(refreshBtn);

        JButton markPresentAllBtn = new JButton("Mark All Present");
        markPresentAllBtn.addActionListener(e -> markAllForDate("Present"));
        topControls.add(markPresentAllBtn);

        JButton markAbsentAllBtn = new JButton("Mark All Absent");
        markAbsentAllBtn.addActionListener(e -> markAllForDate("Absent"));
        topControls.add(markAbsentAllBtn);

        JButton exportAttendBtn = new JButton("Export Attendance CSV");
        exportAttendBtn.addActionListener(e -> exportAttendanceCsv());
        topControls.add(exportAttendBtn);

        center.add(topControls, BorderLayout.NORTH);

        // Middle area: attendance table
        String[] atCols = new String[] {"ID", "Roll", "Name", "Status", "Check-in", "Check-out"};
        attendanceTableModel = new DefaultTableModel(atCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                // allow status editing (col 3)
                return col == 3 || col == 4 || col == 5;
            }
        };
        attendanceTable = new JTable(attendanceTableModel);
        attendanceTable.setRowHeight(26);
        TableColumn statusCol = attendanceTable.getColumnModel().getColumn(3);
        JComboBox<String> statusCombo = new JComboBox<>(new String[]{"Present","Absent","Late"});
        statusCol.setCellEditor(new DefaultCellEditor(statusCombo));

        JScrollPane attScroll = new JScrollPane(attendanceTable);
        attScroll.setPreferredSize(new Dimension(600, 250));
        center.add(attScroll, BorderLayout.CENTER);

        // bottom controls for attendance
        JPanel attBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveAttendanceBtn = new JButton("Save Changes");
        saveAttendanceBtn.addActionListener(e -> saveAttendanceFromTable());
        attBottom.add(saveAttendanceBtn);

        JButton markSelectedPresentBtn = new JButton("Mark Selected Present");
        markSelectedPresentBtn.addActionListener(e -> markSelectedRows("Present"));
        attBottom.add(markSelectedPresentBtn);

        JButton markSelectedAbsentBtn = new JButton("Mark Selected Absent");
        markSelectedAbsentBtn.addActionListener(e -> markSelectedRows("Absent"));
        attBottom.add(markSelectedAbsentBtn);

        center.add(attBottom, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);

        // Right - percentages / reports
        JPanel right = new JPanel(new BorderLayout());
        right.setPreferredSize(new Dimension(320, 0));
        right.setBorder(new EmptyBorder(8,8,8,8));
        right.add(new JLabel("Student Attendance %"), BorderLayout.NORTH);

        String[] pCols = new String[] {"Roll", "Name", "PresentDays", "TotalDays", "Percentage"};
        percentTableModel = new DefaultTableModel(pCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        percentTable = new JTable(percentTableModel);
        percentTable.setRowHeight(24);
        JScrollPane pScroll = new JScrollPane(percentTable);
        right.add(pScroll, BorderLayout.CENTER);

        JPanel pButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportPercentBtn = new JButton("Export % CSV");
        exportPercentBtn.addActionListener(e -> exportPercentageCsv());
        pButtons.add(exportPercentBtn);

        right.add(pButtons, BorderLayout.SOUTH);

        add(right, BorderLayout.EAST);

        // interactions
        studentJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateAttendanceTable();
                // optionally select row in percent table
            }
        });
    }

    // ===================== Student actions =====================
    private void loadStudents() {
        studentListModel.clear();
        List<Student> students = db.getAllStudents();
        students.forEach(studentListModel::addElement);
    }

    private void filterStudents() {
        String q = searchField.getText().trim().toLowerCase();
        studentListModel.clear();
        List<Student> students = db.getAllStudents();
        students.stream()
                .filter(s -> s.name.toLowerCase().contains(q) || s.roll.toLowerCase().contains(q))
                .forEach(studentListModel::addElement);
    }

    private void showAddStudentDialog() {
        JTextField nameField = new JTextField();
        JTextField rollField = new JTextField();
        final JComponent[] inputs = new JComponent[] {
                new JLabel("Name"), nameField,
                new JLabel("Roll / ID"), rollField
        };
        int result = JOptionPane.showConfirmDialog(this, inputs, "Add Student", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String roll = rollField.getText().trim();
            if (name.isEmpty() || roll.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name and Roll cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            db.addStudent(new Student(0, name, roll));
            loadStudents();
            updateAttendanceTable();
            updatePercentageTable();
        }
    }

    private void editSelectedStudent() {
        Student s = studentJList.getSelectedValue();
        if (s == null) { JOptionPane.showMessageDialog(this, "Select a student first."); return; }
        JTextField nameField = new JTextField(s.name);
        JTextField rollField = new JTextField(s.roll);
        final JComponent[] inputs = new JComponent[] {
                new JLabel("Name"), nameField,
                new JLabel("Roll / ID"), rollField
        };
        int result = JOptionPane.showConfirmDialog(this, inputs, "Edit Student", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String roll = rollField.getText().trim();
            if (name.isEmpty() || roll.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name and Roll cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            s.name = name;
            s.roll = roll;
            db.updateStudent(s);
            loadStudents();
            updateAttendanceTable();
            updatePercentageTable();
        }
    }

    private void removeSelectedStudent() {
        Student s = studentJList.getSelectedValue();
        if (s == null) { JOptionPane.showMessageDialog(this, "Select a student first."); return; }
        int ans = JOptionPane.showConfirmDialog(this, "Delete student \"" + s.name + "\"? This will also delete their attendance records.", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ans == JOptionPane.YES_OPTION) {
            db.removeStudent(s.id);
            loadStudents();
            updateAttendanceTable();
            updatePercentageTable();
        }
    }

    // ===================== Attendance Table =====================
    private LocalDate selectedDate() {
        Date d = (Date) dateSpinner.getValue();
        Instant instant = d.toInstant();
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void updateAttendanceTable() {
        attendanceTableModel.setRowCount(0);
        LocalDate date = selectedDate();
        List<Student> students = db.getAllStudents();
        Map<Integer, Attendance> attForDate = db.getAttendanceForDate(date);
        for (Student s : students) {
            Attendance a = attForDate.get(s.id);
            String status = a != null ? a.status : "Absent"; // default absent
            String inTime = a != null && a.checkIn != null ? a.checkIn : "";
            String outTime = a != null && a.checkOut != null ? a.checkOut : "";
            attendanceTableModel.addRow(new Object[]{s.id, s.roll, s.name, status, inTime, outTime});
        }
    }

    private void saveAttendanceFromTable() {
        LocalDate date = selectedDate();
        int rows = attendanceTableModel.getRowCount();
        for (int r = 0; r < rows; r++) {
            int sid = ((Number)attendanceTableModel.getValueAt(r,0)).intValue();
            String status = Objects.toString(attendanceTableModel.getValueAt(r,3), "Absent");
            String in = Objects.toString(attendanceTableModel.getValueAt(r,4), "").trim();
            String out = Objects.toString(attendanceTableModel.getValueAt(r,5), "").trim();
            db.upsertAttendance(sid, date, status, in, out);
        }
        JOptionPane.showMessageDialog(this, "Attendance saved for " + date.format(DATE_FMT));
        updatePercentageTable();
    }

    private void markSelectedRows(String status) {
        int[] rows = attendanceTable.getSelectedRows();
        if (rows.length == 0) { JOptionPane.showMessageDialog(this, "Select rows to mark."); return; }
        for (int r : rows) {
            attendanceTableModel.setValueAt(status, r, 3);
        }
    }

    private void markAllForDate(String status) {
        for (int r = 0; r < attendanceTableModel.getRowCount(); r++) {
            attendanceTableModel.setValueAt(status, r, 3);
        }
    }

    // ===================== Percentages =====================
    private void updatePercentageTable() {
        percentTableModel.setRowCount(0);
        List<Student> students = db.getAllStudents();
        int totalDays = db.getDistinctAttendanceDaysCount();
        // To be more meaningful, compute per student total recorded days for which student had records or total distinct dates in DB.
        // We'll use the total distinct date count as denominator.
        for (Student s : students) {
            int present = db.getPresentCountForStudent(s.id);
            int days = totalDays;
            double percent = days == 0 ? 0.0 : ((double) present / days) * 100.0;
            percentTableModel.addRow(new Object[]{s.roll, s.name, present, days, String.format("%.1f%%", percent)});
        }
    }

    // ===================== Exports =====================
    private void exportAttendanceCsv() {
        LocalDate date = selectedDate();
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("attendance_" + date.format(DATE_FMT) + ".csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try (BufferedWriter w = Files.newBufferedWriter(f.toPath())) {
            w.write("StudentID,Roll,Name,Status,CheckIn,CheckOut\n");
            for (int r = 0; r < attendanceTableModel.getRowCount(); r++) {
                Object[] row = new Object[attendanceTableModel.getColumnCount()];
                for (int c = 0; c < row.length; c++) row[c] = attendanceTableModel.getValueAt(r, c);
                w.write(String.format("%s,%s,%s,%s,%s,%s\n",
                        row[0], escapeCsv(row[1].toString()), escapeCsv(row[2].toString()),
                        escapeCsv(Objects.toString(row[3],"")), escapeCsv(Objects.toString(row[4], "")),
                        escapeCsv(Objects.toString(row[5], ""))));
            }
            JOptionPane.showMessageDialog(this, "Attendance CSV exported to:\n" + f.getAbsolutePath());
        } catch (IOException ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    private void exportPercentageCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("attendance_percentages.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try (BufferedWriter w = Files.newBufferedWriter(f.toPath())) {
            w.write("Roll,Name,PresentDays,TotalDays,Percentage\n");
            for (int r = 0; r < percentTableModel.getRowCount(); r++) {
                w.write(String.format("%s,%s,%s,%s,%s\n",
                        escapeCsv(percentTableModel.getValueAt(r,0).toString()),
                        escapeCsv(percentTableModel.getValueAt(r,1).toString()),
                        percentTableModel.getValueAt(r,2).toString(),
                        percentTableModel.getValueAt(r,3).toString(),
                        percentTableModel.getValueAt(r,4).toString()));
            }
            JOptionPane.showMessageDialog(this, "Percentages CSV exported to:\n" + f.getAbsolutePath());
        } catch (IOException ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    private void exportStudentsCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("students.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try (BufferedWriter w = Files.newBufferedWriter(f.toPath())) {
            w.write("ID,Roll,Name\n");
            for (int i = 0; i < studentListModel.size(); i++) {
                Student s = studentListModel.get(i);
                w.write(String.format("%d,%s,%s\n", s.id, escapeCsv(s.roll), escapeCsv(s.name)));
            }
            JOptionPane.showMessageDialog(this, "Students CSV exported to:\n" + f.getAbsolutePath());
        } catch (IOException ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ===================== Main =====================
    public static void main(String[] args) {
        try {
            Database db = new Database(DB_FILE);
            SwingUtilities.invokeLater(() -> {
                AttendEase app = new AttendEase(db);
                app.setVisible(true);
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to start: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===================== Models =====================
    static class Student {
        int id;
        String name;
        String roll;
        Student(int id, String name, String roll) { this.id = id; this.name = name; this.roll = roll; }
        @Override public String toString() { return String.format("%s — %s", roll, name); }
    }

    static class Attendance {
        int id;
        int studentId;
        LocalDate date;
        String status; // Present / Absent / Late
        String checkIn;
        String checkOut;
        Attendance(int id, int studentId, LocalDate date, String status, String checkIn, String checkOut) {
            this.id=id; this.studentId=studentId; this.date=date; this.status=status; this.checkIn=checkIn; this.checkOut=checkOut;
        }
    }

    // ===================== Database helper =====================
    static class Database {
        private final String url;
        private Connection conn;

        Database(String dbFile) throws SQLException {
            this.url = "jdbc:sqlite:" + dbFile;
            connect();
            initSchema();
        }

        private void connect() throws SQLException {
            conn = DriverManager.getConnection(url);
        }

        private void initSchema() throws SQLException {
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON;");
                s.execute("CREATE TABLE IF NOT EXISTS students (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL, " +
                        "roll TEXT NOT NULL UNIQUE" +
                        ");");
                s.execute("CREATE TABLE IF NOT EXISTS attendance (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "student_id INTEGER NOT NULL, " +
                        "date TEXT NOT NULL, " + // 'YYYY-MM-DD'
                        "status TEXT NOT NULL, " +
                        "check_in TEXT, " +
                        "check_out TEXT, " +
                        "FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE, " +
                        "UNIQUE(student_id, date)" +
                        ");");
            }
        }

        // Students
        List<Student> getAllStudents() {
            List<Student> out = new ArrayList<>();
            String sql = "SELECT id, name, roll FROM students ORDER BY roll COLLATE NOCASE";
            try (PreparedStatement p = conn.prepareStatement(sql);
                 ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    out.add(new Student(rs.getInt(1), rs.getString(2), rs.getString(3)));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return out;
        }

        void addStudent(Student s) {
            String sql = "INSERT INTO students(name, roll) VALUES(?,?)";
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setString(1, s.name);
                p.setString(2, s.roll);
                p.executeUpdate();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null, "Failed to add student: " + e.getMessage());
            }
        }

        void updateStudent(Student s) {
            String sql = "UPDATE students SET name=?, roll=? WHERE id=?";
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setString(1, s.name);
                p.setString(2, s.roll);
                p.setInt(3, s.id);
                p.executeUpdate();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null, "Failed to update student: " + e.getMessage());
            }
        }

        void removeStudent(int studentId) {
            String sql = "DELETE FROM students WHERE id=?";
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setInt(1, studentId);
                p.executeUpdate();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null, "Failed to remove student: " + e.getMessage());
            }
        }

        // Attendance
        Map<Integer, Attendance> getAttendanceForDate(LocalDate date) {
            Map<Integer, Attendance> map = new HashMap<>();
            String sql = "SELECT id, student_id, date, status, check_in, check_out FROM attendance WHERE date = ?";
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setString(1, date.format(DATE_FMT));
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        Attendance a = new Attendance(
                                rs.getInt("id"),
                                rs.getInt("student_id"),
                                LocalDate.parse(rs.getString("date")),
                                rs.getString("status"),
                                rs.getString("check_in"),
                                rs.getString("check_out")
                        );
                        map.put(a.studentId, a);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return map;
        }

        void upsertAttendance(int studentId, LocalDate date, String status, String checkIn, String checkOut) {
            String sql = "INSERT INTO attendance(student_id, date, status, check_in, check_out) VALUES(?,?,?,?,?) " +
                         "ON CONFLICT(student_id, date) DO UPDATE SET status=excluded.status, check_in=excluded.check_in, check_out=excluded.check_out";
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setInt(1, studentId);
                p.setString(2, date.format(DATE_FMT));
                p.setString(3, status);
                p.setString(4, checkIn.isEmpty() ? null : checkIn);
                p.setString(5, checkOut.isEmpty() ? null : checkOut);
                p.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        int getPresentCountForStudent(int studentId) {
            String sql = "SELECT COUNT(*) FROM attendance WHERE student_id = ? AND status = 'Present'";
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setInt(1, studentId);
                try (ResultSet rs = p.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0;
        }

        int getDistinctAttendanceDaysCount() {
            String sql = "SELECT COUNT(DISTINCT date) FROM attendance";
            try (PreparedStatement p = conn.prepareStatement(sql);
                 ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) { e.printStackTrace(); }
            return 0;
        }
    }
}

