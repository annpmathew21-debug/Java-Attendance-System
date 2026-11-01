package com.attendease.model;

import java.time.LocalDate;

public class Attendance {
    private int id;
    private int studentId;
    private LocalDate date;
    private String status;
    private String checkIn;
    private String checkOut;

    public Attendance(int id, int studentId, LocalDate date, String status, String checkIn, String checkOut) {
        this.id = id;
        this.studentId = studentId;
        this.date = date;
        this.status = status;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    public int getId() { return id; }
    public int getStudentId() { return studentId; }
    public LocalDate getDate() { return date; }
    public String getStatus() { return status; }
    public String getCheckIn() { return checkIn; }
    public String getCheckOut() { return checkOut; }

    public void setStatus(String status) { this.status = status; }
    public void setCheckIn(String checkIn) { this.checkIn = checkIn; }
    public void setCheckOut(String checkOut) { this.checkOut = checkOut; }
}

