package com.volard.TrackingApp;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.List;

@Entity
public class Employee {
    @PrimaryKey
    public int uid;

    @ColumnInfo(name = "first_name")
    public String firstName;

    @ColumnInfo(name = "last_name")
    public String lastName;

    @ColumnInfo(name = "last_time_responded")
    public String lastTimeResponded;

    @ColumnInfo(name = "active")
    public boolean active;

    @ColumnInfo(name = "last_location")
    public List<Double> location;
}
