package com.volard.TrackingApp;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EmployeeDao {
        @Query("SELECT * FROM employee")
        List<Employee> getAll();

        @Query("SELECT * FROM employee WHERE uid IN (:userIds)")
        List<Employee> loadAllByIds(int[] userIds);

        @Query("SELECT * FROM employee WHERE first_name LIKE :first AND " +
                "last_name LIKE :last LIMIT 1")
        Employee findByName(String first, String last);

        @Insert
        void insertAll(Employee... employees);

        @Delete
        void delete(Employee employee);
}