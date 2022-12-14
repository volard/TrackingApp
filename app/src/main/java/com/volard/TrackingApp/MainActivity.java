package com.volard.TrackingApp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentResultListener;
import androidx.room.Room;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Debug
//    private static final String TAG = "MainActivityLog";
    private static final String TAG = BluetoothService.TAG;


    // TODO place this constant somewhere in local constants or smth
    private final String MAC_address = "22:22:83:A8:01:D6";

    // current context
    private Context context;

    // Map as the fragment
    private MapFragment mapFragment;

    // Intent request codes
    private static final int REQUEST_ENABLE_BT = 3;

    // Name of the connected device
    private String mConnectedDeviceName = null;

    public AppDatabase db;
    public EmployeeDao employeeDao;

    // Connected device
    private BluetoothDevice mDevice;

    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;

    // Save connection with remote device
    private KeepConnection mKeepConnection;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    // Member object for the bluetooth services
    private BluetoothService mBluetoothService = null;


    // Result activity API implementation to request Bluetooth
    ActivityResultLauncher<Intent> requestBluetoothEnable = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Log.i(BluetoothService.TAG, "Bluetooth adapter was successfully enabled");
                } else {
                    try {
                        throw new Exception("Bluetooth adapter wasn't enabled successfully");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
    );


    public void showToast(final String toastMessage) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_SHORT).show());
    }


    /**
     * Makes device discoverable for 300 seconds (5 minutes).
     */
    @SuppressLint("MissingPermission")
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            // TODO figure out why its not 300 seconds (at least in the dialog box I always get only 120)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }


    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(context, "Not connected", Toast.LENGTH_LONG).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBluetoothService.write(send);
        }
    }


    private void proceedRetrieved(String retrievedMessage){

        // Check the update position message
        // The expected structure: {id} {latitude} {longitude}
        String[] chunks = retrievedMessage.trim().split(" ");
        if (chunks.length != 3){
            Log.d(TAG, "Retrieved message has wrong structure (chunks != 3)");
        }
        double latitude, longitude;
        try
        {
            latitude = Double.parseDouble(chunks[1]);
            longitude = Double.parseDouble(chunks[2]);
        }
        catch(NumberFormatException e)
        {
            Log.d(TAG, "Two last chunks of retrieved message is not valid coordinates");
            return;
        }
        // I assume id is a integer number because its probably will be a primary key in db
        int id;
        try {
           id = Integer.parseInt(chunks[0]);
        }
        catch (NumberFormatException e){
            Log.d(TAG, "The id is not valid integer");
            return;
        }

        if (mapFragment != null){
            mapFragment.updateMarkerPosition(id, latitude, longitude);
        }
    }


    /**
     * Updates the subtitle on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(@NotNull CharSequence subTitle) {
        final ActionBar actionBar = getSupportActionBar();

        if (actionBar == null) {
            Log.i(TAG, "Action bar doesn't support here");
            return;
        }
        actionBar.setSubtitle(subTitle);
    }


    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    @SuppressLint("HandlerLeak")
    // TODO change that to https://stackoverflow.com/questions/61023968/what-do-i-use-now-that-handler-is-deprecated
            Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == Constants.MESSAGE_STATE_CHANGE) {
                if (msg.arg1 == BluetoothService.STATE_CONNECTED) {
                    setStatus("@string/state_connected" + " " + mConnectedDeviceName);
                }
                else if (msg.arg1 == BluetoothService.STATE_CONNECTING) {
                    setStatus("@string/state_connecting");
                }
                else if (msg.arg1 == BluetoothService.STATE_NONE) {
                    setStatus("@string/state_not_connected");
                    // NOTE when KeepConnection thread already exists and trying to do reconnect
                    // it has WAITING state so stuff like isInterrupted() or isAlive() don't
                    // works correctly
                    Log.v(TAG, "Interruption status for KeepConnection = " + mKeepConnection.getState());
                    if (mKeepConnection.getState() == Thread.State.TERMINATED && mDevice != null) {
                        mKeepConnection = new KeepConnection(mDevice);
                        mKeepConnection.start();
                        Log.i(TAG, "Created new mKeepConnection object to reconnecting");
                    }
                }
            }
            else if (msg.what == Constants.MESSAGE_WRITE) {
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                Log.i(TAG, "Bluetooth worker thread: Sent message: " + writeMessage + " : to " + mConnectedDeviceName);
//                mConversationArrayAdapter.add("Me:  " + writeMessage);
            }
            else if (msg.what == Constants.MESSAGE_READ) {
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                Log.i(TAG, "Bluetooth worker thread: " + mConnectedDeviceName + " send to this device:  " + readMessage);
                proceedRetrieved(readMessage);
            }
            else if (msg.what == Constants.MESSAGE_DEVICE_NAME) {
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                if (null != context) {
                    Toast.makeText(context, "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Bluetooth worker thread: Device " + mConnectedDeviceName + " was connected");
                }
            }
            else if (msg.what == Constants.MESSAGE_TOAST) {
                if (context != null) {
                    Toast.makeText(context, msg.getData().getString(Constants.TOAST),
                            Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Bluetooth worker thread: Toast message returned from the bluetooth worker thread: "
                            + msg.getData().getString(Constants.TOAST));
                }
            }
        }
    };


    /**
     * Periodically tries to reconnect to needed remote device
     */
    private class KeepConnection extends Thread {
        private final BluetoothDevice device;

        public KeepConnection(BluetoothDevice device) {
            this.device = device;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            while (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                mBluetoothService.connect(device, true);
                mConnectedDeviceName = device.getName();

                if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
                    showToast("Trying to reconnect in few seconds...");
                    try {
                        Thread.sleep(7000);
                    } catch (InterruptedException e) {
                        // This shouldn't appear but shit happens everywhere...
                        Log.wtf(TAG, "Error occurred: ", e);
                    }
                } else {
                    Log.i(TAG, "Device with provided address didn't connect");
                }
            }
            Log.v(TAG, "KeepConnection: Im interrupted here!");
        }
    }


    // ============================== ACTIVITY LIFECYCLE OVERRIDES ==============================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapFragment = (MapFragment)
                getSupportFragmentManager().findFragmentById(R.id.fragmentContainerView);

        context = getApplicationContext();

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null && getApplicationContext() != null) {
            Toast.makeText(getApplicationContext(), "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }

        // TODO determine if it enough to replace condition above
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Set listener to get data coming from the map fragment
        this.getSupportFragmentManager().setFragmentResultListener("requestKey",
            this,
            new FragmentResultListener() {
                @Override
                public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                    // We use a String here, but any type that can be put in a Bundle is supported
                    String result = bundle.getString("bundleKey");
                    // Do something with the result
                    Log.i(TAG, "Stuff from child activity comes up: " + result);


                    // Create the object of AlertDialog Builder class
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                    builder.setMessage("@string/question_make_sure_to_send_signal");
                    builder.setTitle("@string/attention_label");

                    // Set Cancelable false for when the user clicks on the outside the Dialog Box then it will remain show
                    builder.setCancelable(true);

                    // Set the positive button with yes name Lambda OnClickListener method is use of DialogInterface interface.
                    builder.setPositiveButton("@string/answer_ok", (DialogInterface.OnClickListener) (dialog, which) -> {
                        // When the user click yes button then app will close
                        sendMessage("Bzzzz to " + result);
                    });

                    // Set the Negative button with No name Lambda OnClickListener method is use of DialogInterface interface.
                    builder.setNegativeButton("@string/answer_cancel", (DialogInterface.OnClickListener) (dialog, which) -> {
                        // If user click no then dialog box is canceled.
                        dialog.cancel();
                    });

                    // Create the Alert dialog
                    AlertDialog alertDialog = builder.create();
                    // Show the Alert Dialog box
                    alertDialog.show();
                }
            });

        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "EmployeeDatabase").build();

        employeeDao = db.employeeDao();
    }


    /**
     * Updates the language of application by creating
     * object of inbuilt Locale class and passing language argument to it
     */
    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);

        return context.createConfigurationContext(configuration);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // If Bluetooth is supported at all
        if (mBluetoothAdapter == null) {
            return;
        }

        // Determine what permission needed according to SDK version
        String bluetoothPermission = Manifest.permission.BLUETOOTH;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermission = Manifest.permission.BLUETOOTH_CONNECT;
        }

        // Request enable bluetooth
        while (ActivityCompat.checkSelfPermission(
                context, bluetoothPermission) != PackageManager.PERMISSION_GRANTED) {
            try {
                Log.e(BluetoothService.TAG, "Bluetooth connect permission hasn't been granted");

                Log.d(BluetoothService.TAG, "Trying to get this permission...");
                requestPermissions(new String[]{bluetoothPermission}, REQUEST_ENABLE_BT);
            } catch (Exception ignored) {
            }
        }

        // TODO maybe its a duplication of while logic above
        // If BT is not on, request that it be enabled.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            requestBluetoothEnable.launch(enableBtIntent);
        }

        // Make this device available for others
        ensureDiscoverable();

        // Initialize mBluetoothService to do all bluetooth-stuff
        try {
            mBluetoothService = new BluetoothService(mHandler);
        } catch (Exception e) {
            Log.i(TAG, "Error creating BluetoothService object: " + e.getMessage());
            e.printStackTrace();
        }

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer();

        // Connect to the device with certain MAC address
        if (BluetoothAdapter.checkBluetoothAddress(MAC_address)) {
            mDevice = mBluetoothAdapter.getRemoteDevice(MAC_address);
            Log.i(TAG, "Bluetooth address =  " + MAC_address + " is ok");
            mKeepConnection = new KeepConnection(mDevice);
            mKeepConnection.start();
        }
    }



    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mBluetoothService != null) {
            if (mBluetoothService.getState() == BluetoothService.STATE_NONE) {
                mBluetoothService.startListening();
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop bluetooth service with all it's threads
        if (mBluetoothService != null) {
            mBluetoothService.stop();
        }
        if (mKeepConnection != null && mKeepConnection.isAlive()) {
            mKeepConnection.interrupt();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.appbar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            return true;
        } else if (item.getItemId() == R.id.action_map_theme_toggle) {
            mapFragment.toggleTheme();
            return true;
        } else if (item.getItemId() == R.id.action_toggle_markers_names) {
            mapFragment.toggleTitles();
            return true;
        }
        else if (item.getItemId() == R.id.action_clear_db){
//            employeeDao.delete();
        }
        return false;
}
}