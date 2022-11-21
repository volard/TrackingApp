package com.volard.TrackingApp;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * This class holds app's database.
 * That's main access point to to the persisted data
 */
@Database(entities = {Employee.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract EmployeeDao employeeDao();
}