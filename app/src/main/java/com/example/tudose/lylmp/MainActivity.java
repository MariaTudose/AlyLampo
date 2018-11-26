package com.example.tudose.lylmp;

import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.TimePicker;
import android.app.TimePickerDialog;
import android.view.View;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;
import java.lang.Math;

public class MainActivity extends AppCompatActivity {

    double wantedTemp = 21.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTemp = findViewById(R.id.temp);

        final TextView myTime = (TextView) findViewById(R.id.mytime);

        myTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar currentTime = Calendar.getInstance();
                int hour = currentTime.get(Calendar.HOUR_OF_DAY);
                int minute = currentTime.get(Calendar.MINUTE);

                TimePickerDialog mTimePicker;
                mTimePicker = new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        myTime.setText(String.format("%02d", selectedHour) + ":" + String.format("%02d", selectedMinute));
                    }
                }, hour, minute, true);
                mTimePicker.setTitle("Select Time");
                mTimePicker.show();

            }
        });


        Handler handler = new Handler(Looper.getMainLooper()) {
            /*
              * handleMessage() defines the operations to perform when
              * the Handler receives a new Message to process.
              */
            @Override
            public void handleMessage(Message inputMessage) {
                Log.e("handleMessage", inputMessage.toString());
                if(inputMessage.obj == null)
                    Log.e("handleMessage", "array was null");
                else
                Log.e("handleMessage", new String((byte[])inputMessage.obj, 0, 8));
                if(inputMessage.what == MyBluetoothService.MessageConstants.MESSAGE_READ)
                    temperatureChange(inputMessage);
            }
        };

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.e("onCreate", "Device does not support BT!");
            finish();
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        btService = new MyBluetoothService(handler);

        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);
        tryConnect();
    }

    public void displayWantedTemp(double temp) {
        TextView tempView = (TextView) findViewById(R.id.wantedTemp);
        tempView.setText(String.valueOf(temp));
    }

    public void addTemp(View view) {
        if(wantedTemp < 30) wantedTemp++;
        displayWantedTemp(wantedTemp);
    }

    public void subTemp(View view) {
        if(wantedTemp > 15) wantedTemp--;
        displayWantedTemp(wantedTemp);
    }

    public void start(View view) {
        String message = "";

        TextView currentTempView = findViewById(R.id.temp);
        double currentTemp = Double.parseDouble(currentTempView.getText().toString());
        int timeEstimate = (int) Math.abs(currentTemp - wantedTemp) * 2;

        final TextView myTime = (TextView) findViewById(R.id.mytime);
        String[] splitTime = myTime.getText().toString().split(":");

        int hour1 = Integer.parseInt(splitTime[0]);
        int minute1 = Integer.parseInt(splitTime[1]);

        Calendar currentTime = Calendar.getInstance();
        int hour2 = currentTime.get(Calendar.HOUR_OF_DAY);
        int minute2 = currentTime.get(Calendar.MINUTE);

        if (hour2 <= hour1) {
            if(minute2 < minute1 && (minute1 - minute2) > timeEstimate) {
                int hr = hour1 - hour2;
                int minutes = minute1 - minute2 - timeEstimate;
                Log.d("test", Integer.toString(minutes));
                message = "Huoneen lämmityksessä kestää " + timeEstimate + "min\n" +
                        "Lämmitys aloitetaan " + hr + "h " + minutes + "min kuluttua";
            } else {
                int hr = hour1 - hour2 - 1;
                int minutes = 60 - (minute1 + minute2) - timeEstimate;
                Log.d("test", Integer.toString(minutes));
                message = "Huoneen lämmityksessä kestää " + timeEstimate + "min\n" +
                        "Lämmitys aloitetaan " + hr + "h " + minutes + "min kuluttua";
            }
        } else
            message = "Huoneen lämmityksessä kestää " + timeEstimate + "min\n" +
                    "Lämmitys aloitetaan heti";

        TextView tempView = (TextView) findViewById(R.id.message);
        tempView.setText(String.valueOf(message));
    }

    public void stop(View view) {
        TextView tempView = (TextView) findViewById(R.id.message);
        tempView.setText(String.valueOf("Lämmitys ei ole päällä\n"));
    }

    private void tryConnect() {
        if(btService.isConnected()) {
            Log.i("tryConnect", "Already connected");
            return;
        }
        Log.e("onCreate", "Looking through paired devices...");
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        boolean found = false;
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.e("onCreate", "Paired device found :" + deviceName + " " + deviceHardwareAddress);
                if (rpiMatch(deviceName, deviceHardwareAddress)) {
                    found = true;
                    connectBTDevice(device);
                }
            }
        } else {
            Log.e("onCreate", "No paired devices found!");
        }

        if (!found) {
            Log.e("onCreate", "Starting BT discovery since RPI was not paired...");
            mBluetoothAdapter.startDiscovery();
        }
    }

    private boolean rpiMatch(String deviceName, String deviceHardwareAddress) {
        return deviceName.equals("raspberrypi") && deviceHardwareAddress.equals("B8:27:EB:C1:51:99");
    }

    private void connectBTDevice(BluetoothDevice device) {
        Log.e("onReceive", "Found the raspberry, connecting...");
        btDevice = device;
        ConnectThread r = new ConnectThread(device);
        r.start();
    }

    private void manageMyConnectedSocket(BluetoothSocket socket) {
        Log.e("manageMyConnectedSocket", "Connected succesfully!");
        btService.connected(socket);
    }

    private final static int REQUEST_ENABLE_BT = 1;
    private static final String TAG = "SensorsActivity";

    private BluetoothDevice btDevice;
    private UUID MY_UUID = UUID.fromString("7be1fcb3-5776-42fb-91fd-2ee7b5bbb86d");
    private BluetoothAdapter mBluetoothAdapter;
    private MyBluetoothService btService;
    private TextView mTemp;
    private TextView mHumidity;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.e("onReceive", "BT Device discovered: " + deviceName + " " + deviceHardwareAddress);
                if (rpiMatch(deviceName, deviceHardwareAddress)) {
                    connectBTDevice(device);
                }
            }
        }
    };

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            manageMyConnectedSocket(mmSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    void temperatureChange(Message msg) {
        String s = new String((byte[])(msg.obj), 0, 8);
        Log.e("set text", "set text called with " + s.substring(0, 4));
        mTemp.setText(s.substring(0, 4));
//        mHumidity.setText(s.substring(4, 8));
    }
}
