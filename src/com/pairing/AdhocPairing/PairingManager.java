package com.pairing.AdhocPairing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PairingManager {
	// Debugging
    private static final String TAG = "PairingManager";
    private static final boolean D = false;
    
    // Name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChat";

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a67");

	// Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
	
    // Max connection number
    public static final int MAX_CONNECTION = 10;
    
	// Member fields
    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private int mState;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private TransmitThread mTransmitThread;
    private ArrayList<TransmitThread> mTransmitThreadArrayList;
	
    /**
     * Constructor. Prepares a new Pairing session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
	public PairingManager(Handler handler) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        mTransmitThreadArrayList = new ArrayList<TransmitThread>(MAX_CONNECTION);
        if (D) postStatus(TAG + ": constructor()");
    }
	
	/**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        if (D) postStatus(TAG + ": state change: " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
	
	/**
     * Return the current connection state. */
	public synchronized int getState() {
        return mState;
    }
	
	/**
     * Start the pairing service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
	public synchronized void doListening() {
        if (D) Log.d(TAG, "listening");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mTransmitThread != null) {mTransmitThread.cancel(); mTransmitThread = null;}

        // Start the thread to listen on a BluetoothServerSocket
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(STATE_LISTEN);
    }
	
	/**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void doConnecting(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mTransmitThread != null) {mTransmitThread.cancel(); mTransmitThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }
    
    /**
     * Start the TransmitThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void doTransmission(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "doTransmission");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mTransmitThread != null) {mTransmitThread.cancel(); mTransmitThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mTransmitThread = new TransmitThread(socket, device);
        mTransmitThread.start();
        
        // Put this TransmitThread into ArrayList
        mTransmitThreadArrayList.add(mTransmitThread);
        mTransmitThread = null;

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_ADD_DEVICE, device);
        mHandler.sendMessage(msg);
        
        // Post a status
        postStatus(TAG + ".doTransmission() connected to " + device.getName());

        setState(STATE_CONNECTED);
    }
    
    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mTransmitThread != null) {mTransmitThread.cancel(); mTransmitThread = null;}
        for ( TransmitThread r : mTransmitThreadArrayList ) {
        	r.cancel();
        	mTransmitThreadArrayList.remove(r);
        }
        setState(STATE_NONE);
    }
    
    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out, BluetoothDevice device) {
        // Find the associative thread
    	TransmitThread r = null;
    	synchronized (this) {
    		for ( TransmitThread t : mTransmitThreadArrayList ) {
    			if (t.getDevice().equals(device)) {
    				r = t;
        			break;
    			}
    		}
    	}
        // Perform the write unsynchronized
    	if (r != null) {
    		if (D) postStatus(TAG + ".write() to " + device.getName());
    		r.write(out);
    	} else {
    		if (D) postStatus(TAG + ".write() no TransmitThread a/w " + device.getName());
    	}
    }
    
    private void postStatus(String status) {
    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_POST);
        Bundle bundle = new Bundle();
        bundle.putString(AdhocPairingActivity.STATUS, status);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
	
    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_LISTEN);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(AdhocPairingActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost(TransmitThread thread, BluetoothDevice device) {
    	// Remove the lost device's transmission thread out of the ArrayList
    	mTransmitThreadArrayList.remove(thread);
    	
    	// If there is only the thread that is being stopped, start listening
    	if ( mTransmitThreadArrayList.size() == 0 ) {
    		doListening();
    	}
        
        // Send the lost device back to the Activity
        mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_DEVICE_LOST, device)
        		.sendToTarget();
    }
    
    public void requireMoreCommitData(BluetoothDevice device, int n, int m) {
    	TransmitThread r = null;
    	synchronized (this) {
    		for ( TransmitThread t : mTransmitThreadArrayList ) {
    			if (t.getDevice().equals(device)) {
    				r = t;
        			break;
    			}
    		}
    	}
    	r.markCommitData(n, m);
    }
    
	
	/**
	 * This thread runs while listening for incoming connections. It behaves
	 * like a server-side client. It runs until a connection is accepted
	 * (or until canceled).
	 */
	private class AcceptThread extends Thread {
	    // The local server socket
	    private final BluetoothServerSocket mmServerSocket;
	
	    public AcceptThread() {
	        BluetoothServerSocket tmp = null;
	        // Create a new listening server socket
	        try {
	            tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
	        } catch (IOException e) {
	            Log.e(TAG, "listen() failed", e);
	        }
	        mmServerSocket = tmp;
	    }
	
	    public void run() {
	        if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
	        if (D) postStatus("AcceptThread.run() begin listening");
	        setName("AcceptThread");
	        BluetoothSocket socket = null;
	
	        // Listen to the server socket if we're not connected
	        while (mState != STATE_CONNECTED) {
	            try {
	                // This is a blocking call and will only return on a
	                // successful connection or an exception
	                socket = mmServerSocket.accept();
	            } catch (IOException e) {
	                Log.e(TAG, "accept() failed", e);
	                break;
	            }
	
	            // If a connection was accepted
	            if (socket != null) {
	                synchronized (PairingManager.this) {
	                    switch (mState) {
	                    case STATE_LISTEN:
	                    case STATE_CONNECTING:
	                        // Situation normal. Start the connected thread.
	                        doTransmission(socket, socket.getRemoteDevice());
	                        break;
	                    case STATE_NONE:
	                    case STATE_CONNECTED:
	                        // Either not ready or already connected. Terminate new socket.
	                        try {
	                            socket.close();
	                        } catch (IOException e) {
	                            Log.e(TAG, "Could not close unwanted socket", e);
	                        }
	                        break;
	                    }
	                }
	            }
	        }
	        if (D) Log.i(TAG, "END mAcceptThread");
	        if (D) postStatus("AcceptThread.run() end");
	    }
	
	    public void cancel() {
	        if (D) Log.d(TAG, "cancel " + this);
	        try {
	            mmServerSocket.close();
	        } catch (IOException e) {
	            Log.e(TAG, "close() of server failed", e);
	        }
	    }
	}


	/**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            if (D) postStatus("ConnectThread.run() connecting to " + mmDevice.getName());
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                postStatus("ConnectThread.run() failed");
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                if (mTransmitThreadArrayList.size() == 0)
                	PairingManager.this.doListening();
                return;
            }
            if (D) postStatus("ConnectThread.run() connected to " + mmDevice.getName());
            // Reset the ConnectThread because we're done
            synchronized (PairingManager.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            doTransmission(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class TransmitThread extends Thread {
    	private int maxBufferSize = 5000;
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        // Signals for requiring more commit data
        int flagCommit;
        int nCommit, mCommit;
        byte[] bufferCommit;
        
        public TransmitThread(BluetoothSocket socket, BluetoothDevice device) {
            Log.d(TAG, "create TransmitThread");
            mmDevice = device;
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            
            // Data for commit process 
            flagCommit = 0;
            nCommit = 0;
            mCommit = 0;
            bufferCommit = null;
        }

        public void run() {
            Log.i(TAG, "BEGIN mTransmitThread");
            if (D) postStatus("TransmitThread.run() begin with " + mmDevice.getName());
            byte[] buffer = new byte[maxBufferSize];
            int bytes;
            
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                	bytes = 0;
                    bytes = mmInStream.read(buffer, bytes, buffer.length);
                    
                    // Get the command code
                    byte command = buffer[0];
                    
                    // In case of flagCommit == 1 (waiting for next part of data commit)
                    if (flagCommit == 1) {
                    	if ( 4*(nCommit+mCommit+2) > maxBufferSize )
                    		maxBufferSize = 4 * (nCommit + mCommit + 2);
                    	byte[] tbuffer = new byte[maxBufferSize];
                    	for (int i=0; i<bufferCommit.length; i++)
                    		tbuffer[i] = bufferCommit[i];
                    	for (int i=0; i<bytes; i++)
                    		tbuffer[bufferCommit.length+i] = buffer[i];
                    	buffer = tbuffer;
                    	bytes += bufferCommit.length;
                    	flagCommit = 0;
                    	command = buffer[0];
                    }

                    // Send the obtained command and sending device to the UI Activity
                    if (bytes == 1) { 
                    	
                    	mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_RECEIVE_COMMAND, command, -1, mmDevice)
                            	.sendToTarget();
                    	
                    } else if (bytes == 9 && command == AdhocPairingActivity.COMMAND_GET_SAMPLE) {
                    	
                    	Log.i(TAG, "Received COMMAND_GET_SAMPLE");
                    	if (D) postStatus(TAG + ": Received COMMAND_GET_SAMPLE");
                    	byte[] buffer2 = new byte[8];
                    	for (int i=0; i<8; i++)
                    		buffer2[i] = buffer[i+1];
                    	Bundle bundle = new Bundle();
                    	bundle.putByteArray(AdhocPairingActivity.BYTE_ARRAY, buffer2);
                    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_RECEIVE_COMMAND, command, -1, mmDevice);
                    	msg.setData(bundle);
                    	mHandler.sendMessage(msg);
                    	
                    } else if ((command == AdhocPairingActivity.COMMAND_RECEIVE_MY_FINGERPRINT) && (bytes == AudioFingerprint.fingerprintBits + 1) ) {
                    	
                    	Log.i(TAG, "Received COMMAND_RECEIVE_MY_FINGERPRINT");
                    	if (D) postStatus(TAG + ": Received COMMAND_RECEIVE_MY_FINGERPRINT");
                    	int n = AudioFingerprint.fingerprintBits;
                    	byte[] buffer2 = new byte[n];
                    	for (int i=0; i<n; i++)
                    		buffer2[i] = buffer[i+1];
                    	Bundle bundle = new Bundle();
                    	bundle.putByteArray(AdhocPairingActivity.BYTE_ARRAY, buffer2);
                    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_RECEIVE_COMMAND, command, -1, mmDevice);
                    	msg.setData(bundle);
                    	mHandler.sendMessage(msg);
                    	
                    } else if (command == AdhocPairingActivity.COMMAND_RECEIVE_STRING) {
                    	
                    	byte[] buffer2 = new byte[bytes-1];
                    	for (int i=0; i<buffer2.length; i++)
                    		buffer2[i] = buffer[i+1];
                    	// Or post the received String onto Status Board
                    	postStatus(mmDevice.getName() + ": " + (new String(buffer2, 0, bytes-1)));
                    	
                    } else if (command == AdhocPairingActivity.COMMAND_RECEIVE_COMMIT) {
                    	
                    	Log.i(TAG, "Received COMMAND_RECEIVE_COMMIT");
                    	if (D) postStatus(TAG + ": Received COMMAND_RECEIVE_COMMIT");
                    	byte[] buffer2 = new byte[bytes-1];
                    	for (int i=0; i<bytes-1; i++)
                    		buffer2[i] = buffer[i+1];
                    	Bundle bundle = new Bundle();
                    	bundle.putByteArray(AdhocPairingActivity.BYTE_ARRAY, buffer2);
                    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_RECEIVE_COMMAND, command, -1, mmDevice);
                    	msg.setData(bundle);
                    	mHandler.sendMessage(msg);
                    	// Backup the buffer
                    	bufferCommit = new byte[bytes];
                    	for (int i=0; i<bytes; i++)
                    		bufferCommit[i] = buffer[i];
                    	try {
                    		Thread.sleep(1000);
                    	} catch (InterruptedException e) {
                    		postStatus(e.getMessage());
                    	}
                    	
                    } else if (command == AdhocPairingActivity.COMMAND_CREATE_COMPARISON_TABLE) {
                    	
                    	Log.i(TAG, "Received COMMAND_CREATE_COMPARISON_TABLE");
                    	if (D) postStatus(TAG + ": Received COMMAND_CREATE_COMPARISON_TABLE");
                    	int n = AudioFingerprint.fingerprintBits;
                    	byte[] buffer2 = new byte[n];
                    	for (int i=0; i<n; i++)
                    		buffer2[i] = buffer[i+1];
                    	Bundle bundle = new Bundle();
                    	bundle.putByteArray(AdhocPairingActivity.BYTE_ARRAY, buffer2);
                    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_RECEIVE_COMMAND, command, -1, mmDevice);
                    	msg.setData(bundle);
                    	mHandler.sendMessage(msg);
                    	
                    } else if (command == AdhocPairingActivity.COMMAND_CREATE_COMPARISON_TABLE_2) {
                    	
                    	Log.i(TAG, "Received COMMAND_CREATE_COMPARISON_TABLE_2");
                    	if (D) postStatus(TAG + ": Received COMMAND_CREATE_COMPARISON_TABLE_2");
                    	int n = AudioFingerprint.fingerprintBits;
                    	byte[] buffer2 = new byte[n];
                    	for (int i=0; i<n; i++)
                    		buffer2[i] = buffer[i+1];
                    	Bundle bundle = new Bundle();
                    	bundle.putByteArray(AdhocPairingActivity.BYTE_ARRAY, buffer2);
                    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_RECEIVE_COMMAND, command, -1, mmDevice);
                    	msg.setData(bundle);
                    	mHandler.sendMessage(msg);
                    	
                    } else if (command == AdhocPairingActivity.COMMAND_CREATE_COMPARISON_TABLE_3) {
                    	
                    	Log.i(TAG, "Received COMMAND_CREATE_COMPARISON_TABLE_3");
                    	if (D) postStatus(TAG + ": Received COMMAND_CREATE_COMPARISON_TABLE_3");
                    	int n = AudioFingerprint.fingerprintBits;
                    	byte[] buffer2 = new byte[n];
                    	for (int i=0; i<n; i++)
                    		buffer2[i] = buffer[i+1];
                    	Bundle bundle = new Bundle();
                    	bundle.putByteArray(AdhocPairingActivity.BYTE_ARRAY, buffer2);
                    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_RECEIVE_COMMAND, command, -1, mmDevice);
                    	msg.setData(bundle);
                    	mHandler.sendMessage(msg);
                    	
                    } else {
                    	
                    	// Undefined received data
                    	postStatus("Received undefined data, " + Integer.toString(bytes) + " bytes.");
                    	
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost(TransmitThread.this, mmDevice);
                    break;
                }
            }
            
            if (D) postStatus("TransmitThread.run() end with " + mmDevice.getName());
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
        	if (D) postStatus("TransmitThread.write() to " + mmDevice.getName());
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
                postStatus("TransmitThread.write() exception " + mmDevice.getName());
            }
            if (D) postStatus("TransmitThread.write() finished " + mmDevice.getName());
        }

        public BluetoothDevice getDevice() {
        	return mmDevice;
        }
        
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
                postStatus("TransmitThread.close(): cannot close socket");
            }
        }
        
        public void markCommitData(int n, int m) {
        	this.flagCommit = 1;
        	this.nCommit = n;
        	this.mCommit = m;
        }
    }
}
