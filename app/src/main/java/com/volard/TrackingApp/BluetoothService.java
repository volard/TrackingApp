package com.volard.TrackingApp;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Tools for connection to remote device, initializing
 * listening for incoming connections and exchanging data via RFCOMM protocol
 * and Bluetooth API
 */
public class BluetoothService {
    // Debugging
    static public final String TAG = "BLUETOOTH_SERVICE";

    // Unique UUID for this application
    private static final UUID UUID_INSECURE
            = UUID.fromString("953ef910-3861-4b53-bdd2-8db5ba9fcc72");
    private static final UUID UUID_SECURE
            = UUID.fromString("434ca063-3a16-465f-a8eb-ab59d75fdd8e");


    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothConnectionSecure";
    private static final String NAME_INSECURE = "BluetoothConnectionInsecure";

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    private static final ArrayList<String> stateDescriptions = new ArrayList<String>(){
        {
            add("Doing nothing");
            add("Listening");
            add("Connecting");
            add("Connected");
        }
    };

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private int mNewState;


    /**
     * Returns human readable state description
     * @param stateId id of the state
     * @return state's description
     */
    public synchronized String getStateDescription(int stateId){
        try{
            return stateDescriptions.get(stateId);
        }
        catch (IndexOutOfBoundsException ex){
            return "Unknown state under index " + stateId;
        }
    }


    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param handler A Handler to send messages back to the UI Activity
     */
    BluetoothService(Handler handler){
        mHandler = handler;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mNewState = mState;
    }


    /**
     * Update UI title according to the current state of the chat connection
     */
    private synchronized void updateUserInterfaceTitle() {
        mState = getState();
        Log.i(TAG, "Update state: " + getStateDescription(mNewState) + " -> " + getStateDescription(mState));
        mNewState = mState;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();
    }


    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }


    /**
     * Start AcceptThread to begin a session in listening (server) mode.
     * Called by the Activity onResume()
     */
    public synchronized void startListening() {
        Log.i(TAG, "Start listening");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }

        // Update UI title
        updateUserInterfaceTitle();
    }


    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.i(TAG, "Connecting to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();

        // Update UI title
        updateUserInterfaceTitle();
    }


    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        Log.i(TAG, "Connection lost");
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        BluetoothService.this.startListening();
    }


    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        Log.i(TAG, "Connection failed");

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        BluetoothService.this.startListening();
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final String mSocketType;


        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";


            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                Log.e(TAG, "Unable to connect via defined socket");
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the failed client socket", closeException);
                }

                connectionFailed();
                return;
            }

            Log.i(TAG, "Connection attempt succeeded");

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);

        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the successful client socket", e);
            }
        }
    }


    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    @SuppressLint("MissingPermission")
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.i(TAG, "Connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Update UI title
        updateUserInterfaceTitle();
    }


    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.i(TAG, "Stop bluetooth service");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        mState = STATE_NONE;

        // Update UI title
        updateUserInterfaceTitle();
    }


    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    public class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket; // local Server socket
        private final String mSocketType;

        @SuppressLint("MissingPermission")
        public AcceptThread(boolean secure){
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            UUID.fromString("953ef910-3861-4b53-bdd2-8db5ba9fcc72"));
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE,
                            UUID.fromString("953ef910-3861-4b53-bdd2-8db5ba9fcc72"));
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
            mState = STATE_LISTEN;
        }


        public void run() {
            Log.i(TAG, "AcceptThread run: AcceptThread Running...");

            BluetoothSocket socket; // local socket

            // Keep listening until exception occurs or a lSocket is returned.
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "AcceptThread run: Socket's accept() method failed: " + e.getMessage());
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                                // just keep listening
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice(),
                                        mSocketType);
                                break;
                            case STATE_NONE:
                                // nothing is nothing
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket: " + e.getMessage());
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "AcceptThread run: End AcceptThread.");
        }

        /**
         * Closes the connect socket and causes the thread to finish
         */
        public void cancel() {
            Log.i(TAG, "AcceptThread cancel: Canceling AcceptThread.");
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread cancel: Could not close the connect socket" +
                        e.getMessage());
            }
        }
    }


    /**
     * Write to the ConnectedThread in an un-synchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;

        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }

        // Perform the write un-synchronized
        r.write(out);
    }


    /**
     * Common Thread for data exchanging
     */
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothSocket mmSocket;

        private byte[] buffer; // buffer store for the stream

        /**
         * Initialize streams to manage out/in data
         * @param socket connected socket to get/push data
         */
        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.i(TAG, "create ConnectedThread: " + socketType);

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams
            // using temp objects because member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created" + e.getMessage());
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = STATE_CONNECTED;
        }


        public void run() {
            Log.i(TAG, "ConnectedThread run: Connected thread running...");

            buffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (mState == STATE_CONNECTED) {
                Log.i(TAG, "ConnectedThread run: Connected thread is listening...");
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI activity.
                    Message readMsg = mHandler.obtainMessage(
                            Constants.MESSAGE_READ, numBytes, -1,
                            buffer);
                    readMsg.sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "ConnectedThread run: Input stream was disconnected: " + e.getMessage());
                    connectionLost();
                    break;
                }
            }
        }


        /**
         * Send data to remote device
         * @param bytes sending data
         */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                Message writtenMsg = mHandler.obtainMessage(
                        Constants.MESSAGE_WRITE, -1, -1, buffer);
                writtenMsg.sendToTarget();
                Log.i(TAG, "ConnectedThread write: Wrote to remote device");

            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread write: Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        mHandler.obtainMessage(Constants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);
            }
        }


        /**
         * Shut down connection
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket: " + e.getMessage());
            }
        }
    }

}
