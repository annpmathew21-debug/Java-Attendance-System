package com.attendease.ui;

import com.attendease.db.Database;
import com.attendease.model.Attendance;
import com.attendease.model.Student;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class MainFrame extends JFrame {
    private final Database db;
    private final JTable attendanceTable;
    private final DefaultTableModel tableModel;
    private final JComboBox<String> statusBox;
    private final JTextField checkInField, checkOutField;
    private final JLabel percentageLabel;
    private final JList<Student> studentList;
    private final DefaultListModel<Student> studentListModel;

    private LocalDate selectedDate = LocalDate.now();

    public MainFrame(Database db) {
        this.db = db;
        setTitle("AttendEase - Attendance Management");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // ==== Left panel: Student management ====
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Students"));

        studentListModel = new DefaultListModel<>();
        studentList = new JList<>(studentListModel);
        refreshStudentList();

        leftPanel.add(new JScrollPane(studentList), BorderLayout.CENTER);

        JPanel studentButtons = new JPanel(new GridLayout(1, 3, 5, 5));
        JButton addBtn = new JButton("Add");
        JButton editBtn = new JButton("Edit");
        JButton delBtn = new JButton("Delete");

        addBtn.addActionListener(this::handleAddStudent);
        editBtn.addActionListener(this::handleEditStudent);
        delBtn.addActionListener(this::handleDeleteStudent);

        studentButtons.add(addBtn);
        studentButtons.add(editBtn);
        studentButtons.add(delBtn);

        leftPanel.add(studentButtons, BorderLayout.SOUTH);

        // ==== Center panel: Attendance table ====
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Attendance"));

        String[] cols = {"ID", "Roll", "Name", "Status", "Check-In", "Check-Out"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) {
                return col >= 3; // allow edits only for Status, Check-In, Check-Out
            }
        };
        attendanceTable = new JTable(tableModel);
        refreshAttendanceTable();

        centerPanel.add(new JScrollPane(attendanceTable), BorderLayout.CENTER);

        JPanel bottomControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBox = new JComboBox<>(new String[]{"Present", "Absent"});
        checkInField = new JTextField(6);
        checkOutField = new JTextField(6);
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(this::handleSaveAttendance);

        bottomControls.add(new JLabel("Status:"));
        bottomControls.add(statusBox);
        bottomControls.add(new JLabel("In:"));
        bottomControls.add(checkInField);
        bottomControls.add(new JLabel("Out:"));
        bottomControls.add(checkOutField);
        bottomControls.add(saveBtn);

        centerPanel.add(bottomControls, BorderLayout.SOUTH);

        // ==== Bottom panel: Attendance percentage ====
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        percentageLabel = new JLabel("Select a student to view percentage");
        JButton calcBtn = new JButton("Calculate %");
        calcBtn.addActionListener(this::handleCalculatePercentage);
        bottomPanel.add(calcBtn);
        bottomPanel.add(percentageLabel);

        // ==== Add all panels to frame ====
        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ---- Student Management ----
    private void handleAddStudent(ActionEvent e) {
        JTextField nameField = new JTextField();
        JTextField rollField = new JTextField();
        Object[] msg = {"Name:", nameField, "Roll:", rollField};
        int opt = JOptionPane.showConfirmDialog(this, msg, "Add Student", JOptionPane.OK_CANCEL_OPTION);
        if (opt == JOptionPane.OK_OPTION) {
            if (!nameField.getText().isBlank() && !rollField.getText().isBlank()) {
                db.addStudent(new Student(0, nameField.getText(), rollField.getText()));
                refreshStudentList();
                refreshAttendanceTable();
            }
        }
    }

    private void handleEditStudent(ActionEvent e) {
        Student s = studentList.getSelectedValue();
        if (s == null) return;
        JTextField nameField = new JTextField(s.getName());
        JTextField rollField = new JTextField(s.getRoll());
        Object[] msg = {"Name:", nameField, "Roll:", rollField};
        int opt = JOptionPane.showConfirmDialog(this, msg, "Edit Student", JOptionPane.OK_CANCEL_OPTION);
        if (opt == JOptionPane.OK_OPTION) {
            s.setName(nameField.getText());
            s.setRoll(rollField.getText());
            db.updateStudent(s);
            refreshStudentList();
            refreshAttendanceTable();
        }
    }

    private void handleDeleteStudent(ActionEvent e) {
        Student s = studentList.getSelectedValue();
        if (s == null) return;
        int opt = JOptionPane.showConfirmDialog(this, "Delete " + s.getName() + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            db.removeStudent(s.getId());
            refreshStudentList();
            refreshAttendanceTable();
        }
    }

    private void refreshStudentList() {
        studentListModel.clear();
        for (Student s : db.getAllStudents()) {
            studentListModel.addElement(s);
        }
    }

    // ---- Attendance Management ----
    private void refreshAttendanceTable() {
        tableModel.setRowCount(0);
        List<Student> students = db.getAllStudents();
        Map<Integer, Attendance> map = db.getAttendanceForDate(selectedDate);
        for (Student s : students) {
            Attendance a = map.get(s.getId());
            String status = (a != null) ? a.getStatus() : "";
            String in = (a != null) ? a.getCheckIn() : "";
            String out = (a != null) ? a.getCheckOut() : "";
            tableModel.addRow(new Object[]{s.getId(), s.getRoll(), s.getName(), status, in, out});
        }
    }

    private void handleSaveAttendance(ActionEvent e) {
        int row = attendanceTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a student row first.");
            return;
        }
        int studentId = (int) tableModel.getValueAt(row, 0);
        String status = (String) statusBox.getSelectedItem();
        String checkIn = checkInField.getText();
        String checkOut = checkOutField.getText();
        db.upsertAttendance(studentId, selectedDate, status, checkIn, checkOut);
        refreshAttendanceTable();
    }

    private void handleCalculatePercentage(ActionEvent e) {
        Student s = studentList.getSelectedValue();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Select a student first.");
            return;
        }
        int present = db.getPresentCountForStudent(s.getId());
        int total = db.getDistinctAttendanceDaysCount();
        double percent = (total == 0) ? 0.0 : (100.0 * present / total);
        percentageLabel.setText(String.format("%s: %.1f%% (%d/%d days)", s.getName(), percent, present, total));
    }
}

