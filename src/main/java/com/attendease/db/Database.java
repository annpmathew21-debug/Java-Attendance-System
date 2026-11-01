package com.attendease.db;

import com.attendease.model.Attendance;
import com.attendease.model.Student;

import javax.swing.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Database {
    private final String url;
    private Connection conn;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Database(String dbFile) throws SQLException {
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
                    "date TEXT NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "check_in TEXT, " +
                    "check_out TEXT, " +
                    "FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE, " +
                    "UNIQUE(student_id, date)" +
                    ");");
        }
    }

    // --- Student Operations ---
    public List<Student> getAllStudents() {
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

    public void addStudent(Student s) {
        String sql = "INSERT INTO students(name, roll) VALUES(?,?)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, s.getName());
            p.setString(2, s.getRoll());
            p.executeUpdate();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Failed to add student: " + e.getMessage());
        }
    }

    public void updateStudent(Student s) {
        String sql = "UPDATE students SET name=?, roll=? WHERE id=?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, s.getName());
            p.setString(2, s.getRoll());
            p.setInt(3, s.getId());
            p.executeUpdate();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Failed to update student: " + e.getMessage());
        }
    }

    public void removeStudent(int studentId) {
        String sql = "DELETE FROM students WHERE id=?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, studentId);
            p.executeUpdate();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Failed to remove student: " + e.getMessage());
        }
    }

    // --- Attendance ---
    public Map<Integer, Attendance> getAttendanceForDate(LocalDate date) {
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
                    map.put(a.getStudentId(), a);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public void upsertAttendance(int studentId, LocalDate date, String status, String checkIn, String checkOut) {
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

    public int getPresentCountForStudent(int studentId) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE student_id = ? AND status = 'Present'";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, studentId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getDistinctAttendanceDaysCount() {
        String sql = "SELECT COUNT(DISTINCT date) FROM attendance";
        try (PreparedStatement p = conn.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }
}
