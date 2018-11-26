package com.example.tudose.lylmp;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.example.tudose.lylmp.MyBluetoothService.MessageConstants.MESSAGE_READ;

/**
 * Created by oskarimantere on 17/11/2018.
 */

public class MyBluetoothService {
    private static final String TAG = "MyBluetoothService";
    private Handler mHandler; // handler that gets info from Bluetooth service
    private ConnectedThread mConnectedThread;
    private int mState;
    private boolean lastConnectedMessage = false;
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTED = 1;  // now connected to a remote device

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    public interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;
        public static final int MESSAGE_CONNECTED = 3;
        public static final int MESSAGE_DISCONNECTED = 4;

        // ... (Add other message types here as needed.)
    }

    public MyBluetoothService(Handler handler) {
        mHandler = handler;
    }

    public boolean isConnected() {
        return mState == STATE_CONNECTED;
    }

    public void connected(BluetoothSocket socket) {
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        mState = STATE_CONNECTED;
        sendConnectedMessage();
    }

    private void sendConnectedMessage() {
        if(!lastConnectedMessage) {
            mHandler.sendMessage(mHandler.obtainMessage(MessageConstants.MESSAGE_CONNECTED));
            lastConnectedMessage = true;
        }
    }

    private void sendDisconnectedMessage() {
        if(lastConnectedMessage) {
            mHandler.sendMessage(mHandler.obtainMessage(MessageConstants.MESSAGE_DISCONNECTED));
            lastConnectedMessage = false;
        }
    }

    public void disconnected() {
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mState = STATE_NONE;
        sendDisconnectedMessage();
    }

    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmReadBuffer;

        ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            int numBytes; // bytes returned from read()
            mmReadBuffer = new byte[1024];

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmReadBuffer);
                    // Send the obtained bytes to the UI activity.
                    Message readMsg = mHandler.obtainMessage(
                            MESSAGE_READ, numBytes, -1,
                            mmReadBuffer);
                    readMsg.sendToTarget();
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                sendConnectedMessage();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
//                Message writeErrorMsg =
//                        mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
//                Bundle bundle = new Bundle();
//                bundle.putString("toast",
//                        "Couldn't send data to the other device");
//                writeErrorMsg.setData(bundle);
//                mHandler.sendMessage(writeErrorMsg);
                sendDisconnectedMessage();
            }
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}
