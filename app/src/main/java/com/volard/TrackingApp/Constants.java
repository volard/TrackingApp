package com.volard.TrackingApp;


/**
 * Defines several constants used between {@link BluetoothService} and the UI.
 */
interface Constants {
    // Message types sent from the BluetoothChatService Handler
    int MESSAGE_STATE_CHANGE = 1;
    int MESSAGE_READ = 2;
    int MESSAGE_WRITE = 3;
    int MESSAGE_DEVICE_NAME = 4;
    int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothService Handler
    static String DEVICE_NAME = "device_name";
    static String TOAST = "toast";
}
