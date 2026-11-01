package com.attendease.model;

public class Student {
    private int id;
    private String name;
    private String roll;

    public Student(int id, String name, String roll) {
        this.id = id;
        this.name = name;
        this.roll = roll;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getRoll() { return roll; }

    public void setName(String name) { this.name = name; }
    public void setRoll(String roll) { this.roll = roll; }

    @Override
    public String toString() {
        return roll + " — " + name;
    }
}

