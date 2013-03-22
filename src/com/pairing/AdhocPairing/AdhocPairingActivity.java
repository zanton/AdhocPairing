package com.pairing.AdhocPairing;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AdhocPairingActivity extends Activity {
	// Debugging
	public static final String TAG = "APActivity";
	public static final boolean D = false;
	
	// Default program mode: Debug or not Debug, mainly for orienting commit and decommmit process
	public static final boolean DEFAULT_DEBUG_MODE = true;
	
	// Message types sent from the PairingManager Handler
	public static final int MESSAGE_ADD_DEVICE = 1;
    public static final int MESSAGE_STATE_CHANGE = 2;
    public static final int MESSAGE_TOAST = 3;
    public static final int MESSAGE_POST = 4;
    public static final int MESSAGE_RECEIVE_COMMAND = 5;
    public static final int MESSAGE_DEVICE_LOST = 6;
    public static final int MESSAGE_RECORDING_STATE_CHANGE = 7;
    public static final int MESSAGE_FINISH_CALCULATING_FP = 8;
	
    // Key names of data in Bundle
    public static final String TOAST = "toast";
    public static final String STATUS = "status";
    public static final String BYTE_ARRAY = "byte_array";
    
    // Command codes
    public static final byte COMMAND_SYNC = 1;
    public static final byte COMMAND_GET_SAMPLE = 2;
    public static final byte COMMAND_SEND_BACK_FINGERPRINT = 3;
    public static final byte COMMAND_RECEIVE_MY_FINGERPRINT = 4;
    public static final byte COMMAND_RECEIVE_STRING = 5;
    public static final byte COMMAND_RECEIVE_COMMIT = 6;
    public static final byte COMMAND_CREATE_COMPARISON_TABLE = 7;
    public static final byte COMMAND_SEND_NEXT_FINGERPRINT = 8;
    public static final byte COMMAND_CREATE_COMPARISON_TABLE_2 = 9;
    public static final byte COMMAND_CREATE_COMPARISON_TABLE_3 = 10;
    public static final byte COMMAND_SEND_NEXT_FINGERPRINT_CT3 = 11;
    
	// Intent request codes
    private static final int REQUEST_DEVICE_TO_CONNECT = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_DEVICE_TO_COMPARE = 3;
    private static final int REQUEST_PREFERENCE_EDIT = 4;
    private static final int REQUEST_SHIFT_TIME_COMPARE = 5;
    private static final int REQUEST_DEVICE_TO_COMMIT = 6;
    private static final int REQUEST_DIFF_PERCENT = 7;
    private static final int REQUEST_SHIFT_TIME_CALCULATE_FP = 8;
    private static final int REQUEST_SHIFT_TIME_COMMIT = 9;
    private static final int REQUEST_SHIFT_TIME_DECOMMIT = 10;
    private static final int REQUEST_SHIFT_TIME_SENDBACK_FP = 11;
    private static final int REQUEST_DEVICE_FOR_COMPARISON_TABLE = 12;
    private static final int REQUEST_DEVICE_FOR_COMPARISON_TABLE_2 = 13;
    private static final int REQUEST_DEVICE_FOR_COMPARISON_TABLE_3 = 14;
    
    // Initial number of devices
    private static final int DEVICE_NUMBER = 10;
    
    // Limit of decodability
    private static final double DIFF_LIMIT = 0.3;
    
	// Layout Views
	private TextView mTitle;
	private ListView mConnectedDevicesView;
	private ListView mStatusView;
	
	// Layout View Adapters
	private ArrayAdapter<String> mConnectedDevicesArrayAdapter;
	private ArrayAdapter<String> mStatusArrayAdapter;
	
	// Strings for right title
	private String connectTitle;
	private String audioTitle;
	
	// Tool objects
	public BluetoothAdapter mBluetoothAdapter = null;
	private PairingManager mPairingManager = null;
	private ArrayList<BluetoothDevice> mConnectedDevicesArrayList = null;
	private Timer mTimer = null;
	public AudioFingerprint mAudioFingerprint = null;
	private ECCoder committer = null;
	private ECCoder decommitter = null;
	
	// Temporary objects for comparing process
	private BluetoothDevice deviceToCompare = null;
	private int shiftTimeToCompare = 0;
	private boolean compareAllShift = false;
	private int lastInputtedShiftTime = 0;
	private boolean isWaitingForCalculatingFP = false;
	private BluetoothDevice waitingDevice = null;
	
	// Temporary objects for generation process of comparison table
	// Transmitter
	private BluetoothDevice CT_receiver = null;
	private int CT_currentMatchingPos = 0;
	private boolean CT_waitingFP = false;
	// Receiver
	private BluetoothDevice CT_transmitter = null;
	private boolean CT_onProgress = false;
	private int CT_currentTrial = 0;
	private int CT_currentPattern = 0;
	private boolean CT_waitingFP2 = false;
	private byte[] CT_transmitter_finger = null;
	private int[][] CT_result= new int[AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS][AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS];
	
	// Temporary objects for generation process of comparison table 2
	// Transmitter
	private BluetoothDevice CT2_receiver = null;
	private boolean CT2_result_waitingFP = false;
	// Receiver
	private BluetoothDevice CT2_transmitter = null;
	private boolean CT2_result_waitingFP2 = false;
	private byte[] CT2_transmitter_finger = null;
	private int[] CT2_result_shifttable = {-50, -45, -40, -35, -30, -25, -20, -15, -10, -5,
						   0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
	private int CT2_result_currentshift = 0;
	private int[] CT2_result = new int[21];

	// Temporary objects for generation process of comparison table 3
	// Transmitter
	private BluetoothDevice CT3_receiver = null;
	private int CT3_currentMatchingPos = 0;
	private boolean CT3_waitingFP = false;
	// Receiver
	private BluetoothDevice CT3_transmitter = null;
	private boolean CT3_onProgress = false;
	private int CT3_currentTrial = 0;
	private int CT3_currentPattern = 0;
	private boolean CT3_waitingFP2 = false;
	private byte[] CT3_transmitter_finger = null;
	private int[][] CT3_result= new int[AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS][AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS];

	// Latency for synchronization
	private long time_offset = 0;
	
	// For testing decommit process
	private ECCoder myECCoder = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (D) Log.e(TAG, "++ ON CREATE ++");
        
        // Set up window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
        
        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        
        // Set up strings for title
        connectTitle = "";
        audioTitle = "";
        
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Set initial preferences
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        if (!pref.contains("sampling_time")) 
        	editor.putString("sampling_time", Integer.toString(AudioFingerprint.DEFAULT_SAMPLING_TIME)); 
		if (!pref.contains("delayed_time")) 
			editor.putString("delayed_time", Integer.toString(AudioFingerprint.DEFAULT_DELAYED_TIME));
		if (!pref.contains("redundant_time")) 
			editor.putString("redundant_time", Integer.toString(AudioFingerprint.DEFAULT_REDUNDANT_TIME));
		if (!pref.contains("sampling_rate")) 
			editor.putString("sampling_rate", Integer.toString(AudioFingerprint.DEFAULT_SAMPLING_RATE));
		if (!pref.contains("frame_length")) 
			editor.putString("frame_length", Integer.toString(AudioFingerprint.DEFAULT_FRAME_LENGTH));
		if (!pref.contains("band_length")) 
			editor.putString("band_length", Integer.toString(AudioFingerprint.DEFAULT_BAND_LENGTH));
		if (!pref.contains("debug_mode")) 
			editor.putString("debug_mode", Boolean.toString(DEFAULT_DEBUG_MODE));
		boolean boo = editor.commit();
		Log.i(TAG, "Result of setting initial arguments is: " + Boolean.toString(boo));
    }
    
    @Override
    public void onStart() {
        super.onStart();
        if (D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // initialize() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, initialize 
        } else {
            if (mPairingManager == null) initialize();
        }
    }
    
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "++ ON RESUME ++");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mPairingManager != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mPairingManager.getState() == PairingManager.STATE_NONE) {
              // Start the Bluetooth chat services
            	mPairingManager.doListening();
            }
        }
    }
    
    private void initialize() {
        Log.d(TAG, "initialize()");
        
        // Initialize array adapters
        mConnectedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
        mStatusArrayAdapter = new ArrayAdapter<String>(this, R.layout.status);
        
        // Find and set up the ListView for array adapters
        mConnectedDevicesView = (ListView) findViewById(R.id.connected_devices);
        mConnectedDevicesView.setAdapter(mConnectedDevicesArrayAdapter);
        mStatusView = (ListView) findViewById(R.id.status_frame);
        mStatusView.setAdapter(mStatusArrayAdapter);
        
        // Set no-device connected title
        mConnectedDevicesArrayAdapter.add(getResources().getText(R.string.none_connected).toString());
        
        
        // Post first status
        if (D) addStatus(TAG + ": initialize()");
        
        // Initialize Connected Devices ArrayList
        mConnectedDevicesArrayList = new ArrayList<BluetoothDevice>(DEVICE_NUMBER);
        
        // Initialize the PairingManager
        mPairingManager = new PairingManager(mHandler);
        
        // Initialize timer
        mTimer = new Timer(this, mHandler);
		mTimer.start();
		
		// Initialize AudioFingerprint 
		mAudioFingerprint = new AudioFingerprint(this, mHandler, mTimer);
		mAudioFingerprint.initialize();
		addStatus(mAudioFingerprint.getArgumentsString());
		
		// Update mTitle
        updateTitle();
    }
    
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Pairing Manager 
        if (mPairingManager != null) mPairingManager.stop();
        // Stop the Timer
        if (mTimer != null) mTimer.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if(D) Log.d(TAG, "onActivityResult " + resultCode);
	    switch (requestCode) {
	    
	    case REQUEST_DEVICE_TO_CONNECT:
	        // When DeviceListActivity returns with a device to connect
	        if (resultCode == Activity.RESULT_OK) {
	            // Get the device MAC address
	            String address = data.getExtras()
	                                 .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
	            // Get the BLuetoothDevice object
	            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
	            // Attempt to connect to the device
	            mPairingManager.doConnecting(device);
	        }
	        break;
	        
	    case REQUEST_ENABLE_BT:
	        // When the request to enable Bluetooth returns
	        if (resultCode == Activity.RESULT_OK) {
	            initialize();
	        } else {
	            // User did not enable Bluetooth or an error occured
	            Log.d(TAG, "BT not enabled");
	            Toast.makeText(this, "Bluetooth is not enabled. Leaving.", Toast.LENGTH_SHORT).show();
	            finish();
	        }
	        break;
	        
	    case REQUEST_DEVICE_TO_COMPARE:
	    	// When DeviceSelector returns with a device to compare with
	    	if (resultCode == Activity.RESULT_OK) {
	    		// Get the BluetoothDevice object
	    		BluetoothDevice device = (BluetoothDevice) data.getParcelableExtra(DeviceSelector.DEVICE_OBJECT);
	    		deviceToCompare = device;
	    		// Launch ShiftTimeInputActivity to set the shift time
	        	Intent intent = new Intent(this, ShiftTimeInputActivity.class);
	        	intent.putExtra(ShiftTimeInputActivity.CURRENT_SHIFT_TIME, shiftTimeToCompare);
	        	startActivityForResult(intent, REQUEST_SHIFT_TIME_COMPARE);
	    	}
	    	break;
	
	    case REQUEST_SHIFT_TIME_COMPARE:
	    	if (resultCode == Activity.RESULT_OK) {
	    		boolean all_shift = data.getBooleanExtra(ShiftTimeInputActivity.ALL_SHIFT_TIME, false);
	    		int shiftTime = data.getIntExtra(ShiftTimeInputActivity.SHIFT_TIME, 0);
	    		shiftTimeToCompare = shiftTime;
	    		compareAllShift = all_shift;
	    		compare1(deviceToCompare, shiftTimeToCompare, compareAllShift);
	    	}
	    	break;
	    	
	    case REQUEST_PREFERENCE_EDIT:
	    	onPreferencesChanged();
	    	break;
	    	
	    case REQUEST_DEVICE_TO_COMMIT:
	    	// When DeviceSelector returns with a device to commit to
	    	// ToDo: send delta and plain_word in committer object to the device
	    	if (resultCode == Activity.RESULT_OK) {
	    		BluetoothDevice device = (BluetoothDevice) data.getParcelableExtra(DeviceSelector.DEVICE_OBJECT);
	    		sendTo(COMMAND_RECEIVE_COMMIT, device, committer.getDelta(), committer.getPlainWord());
	    		addStatus("Commit data sent: \nDelta: " + Integer.toString(committer.getDelta().length)
	    				+ " ints\nPlainWord: " + Integer.toString(committer.getPlainWord().length) + " ints");
	    	}
	    	break;
	    	
	    case REQUEST_DIFF_PERCENT:
	    	if (resultCode == Activity.RESULT_OK){
	    		int diff_percent = data.getIntExtra(DiffPercentInputActivity.DIFF_PERCENT, 10);
	    		testDoDecommit(diff_percent);
	    	}
	    	break;
	    	
	    case REQUEST_SHIFT_TIME_CALCULATE_FP:
	    	if (resultCode == Activity.RESULT_OK) {
	    		boolean all_shift = data.getBooleanExtra(ShiftTimeInputActivity.ALL_SHIFT_TIME, false);
	    		int shiftTime = data.getIntExtra(ShiftTimeInputActivity.SHIFT_TIME, 0);
	    		lastInputtedShiftTime = shiftTime;
	    		compareAllShift = all_shift;
	    		// Calculate finger-print
	        	if (!all_shift) {
	        		if (mAudioFingerprint.getFingerprint(shiftTime) == null)
	        			mAudioFingerprint.calculateFingerprint(shiftTime);
	        	} else {
	        		mAudioFingerprint.calculateFPwithAllPossibleShiftTime();
	        	}
	    	}
	    	break;
	    	
	    case REQUEST_SHIFT_TIME_COMMIT:
	    	if (resultCode == Activity.RESULT_OK) {
	    		int shiftTime = data.getIntExtra(ShiftTimeInput2Activity.SHIFT_TIME, 0);
	    		lastInputtedShiftTime = shiftTime;
	    		// Do committing
	    		doCommit(true, shiftTime);
	    	}
	    	break;
	    	
	    case REQUEST_SHIFT_TIME_DECOMMIT:
	    	if (resultCode == Activity.RESULT_OK) {
	    		int shiftTime = data.getIntExtra(ShiftTimeInput2Activity.SHIFT_TIME, 0);
	    		lastInputtedShiftTime = shiftTime;
	    		// Do committing
	    		doDecommit2(true, shiftTime);
	    	}
	    	break;
	
	    case REQUEST_SHIFT_TIME_SENDBACK_FP:
	    	if (resultCode == Activity.RESULT_OK) {
	    		int shiftTime = data.getIntExtra(ShiftTimeInput2Activity.SHIFT_TIME, 0);
	    		lastInputtedShiftTime = shiftTime;

	    		// Calculate FP if it does not exist, and send back the FP
	    		byte[][] finger = mAudioFingerprint.getFingerprint(shiftTime);
	        	if (finger == null) {
	        		mAudioFingerprint.calculateFingerprint(shiftTime);
	        		isWaitingForCalculatingFP = true;
	        	} else {
	        		int n = finger.length;
	        		int m = finger[0].length;
	        		byte[] send = new byte[n*m];
	        		for (int i=0; i<n; i++)
	        			for (int j=0; j<m; j++)
	        				send[i*m + j] = finger[i][j];
	        		sendTo(COMMAND_RECEIVE_MY_FINGERPRINT, waitingDevice, send);
	        	}
	    	}
	    	break;
	    	
	    case REQUEST_DEVICE_FOR_COMPARISON_TABLE:
	    	// When DeviceSelector returns with a device to compare with
	    	if (resultCode == Activity.RESULT_OK) {
	    		// Get the BluetoothDevice object
	    		BluetoothDevice device = (BluetoothDevice) data.getParcelableExtra(DeviceSelector.DEVICE_OBJECT);
	    		CT_receiver = device;
	    		CT_currentMatchingPos = 0;
	    		sendFP_CT();
	    	}
	    	break;
	    	
	    case REQUEST_DEVICE_FOR_COMPARISON_TABLE_2:
	    	// When DeviceSelector returns with a device to compare with
	    	if (resultCode == Activity.RESULT_OK) {
	    		// Get the BluetoothDevice object
	    		BluetoothDevice device = (BluetoothDevice) data.getParcelableExtra(DeviceSelector.DEVICE_OBJECT);
	    		CT2_receiver = device;
	    		sendFP_CT2();
	    	}
	    	break;
	    	
	    case REQUEST_DEVICE_FOR_COMPARISON_TABLE_3:
	    	// When DeviceSelector returns with a device to compare with
	    	if (resultCode == Activity.RESULT_OK) {
	    		// Get the BluetoothDevice object
	    		BluetoothDevice device = (BluetoothDevice) data.getParcelableExtra(DeviceSelector.DEVICE_OBJECT);
	    		CT3_receiver = device;
	    		CT3_currentMatchingPos = 0;
	    		mAudioFingerprint.generateRandomShiftTime();
	    		sendFP_CT3();
	    	}
	    	break;
	    	
	    }
	    
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
	    switch (item.getItemId()) {
	    
	    case R.id.scan:
	        // Launch the DeviceListActivity to see devices and do scan
	        Intent serverIntent = new Intent(this, DeviceListActivity.class);
	        startActivityForResult(serverIntent, REQUEST_DEVICE_TO_CONNECT);
	        return true;
	        
	    case R.id.discoverable:
	        // Ensure this device is discoverable by others
	        ensureDiscoverable();
	        return true;
	        
	    case R.id.sync:
	    	// Command all clients to sync
	    	for (BluetoothDevice device : mConnectedDevicesArrayList) {
	    		if (D) addStatus("Number of devices:" + Integer.toString(mConnectedDevicesArrayList.size()) + "\nSent command COMMAND_SYNC to " + device.getName());
	    		sendTo(COMMAND_SYNC, device);
	    	}
	    	// Do synchronizing itself
	    	long time_offset = doSync();
	    	setTimeOffset(time_offset);
	    	return true;
	    	
	    case R.id.get_sample:
	    	// Order this device to get sample and receive back the sampling time-point
			long timeToSample = getSample(0);
			// Convert this time-point into the atomic time-point
			timeToSample += mTimer.getTimeOffset();
			
			// Back up the recording atomic time
			mAudioFingerprint.setAtomicRecordingTime(timeToSample);
			
			if (D) addStatus(TAG + ": (atomic) time to sample = " + Long.toString(timeToSample));
			// Command all clients to get Audio sample
			for (BluetoothDevice device : mConnectedDevicesArrayList) {
	    		sendTo(COMMAND_GET_SAMPLE, device, toBytes(timeToSample));
	    	}
	    	return true;
	    	
	    case R.id.compare:
	    	// Check if there's sample already
	    	if (!mAudioFingerprint.isDataReady()) {
	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
	                    Toast.LENGTH_SHORT).show();
	    		return true;
	    	}
	    	// Launch DeviceSelector to choose device for comparing
	    	Intent selectorIntent = new Intent(this, DeviceSelector.class);
	    	selectorIntent.putExtra(DeviceSelector.DEVICE_LIST, mConnectedDevicesArrayList);
	    	startActivityForResult(selectorIntent, REQUEST_DEVICE_TO_COMPARE);
	    	return true;
	    
	    case R.id.calculateFP:
	    	// Check if there's sample already
	    	if (!mAudioFingerprint.isDataReady()) {
	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
	                    Toast.LENGTH_SHORT).show();
	    		return true;
	    	}
	    	// Launch ShiftTimeInputActivity to get the shift time
        	Intent intent = new Intent(this, ShiftTimeInputActivity.class);
        	intent.putExtra(ShiftTimeInputActivity.CURRENT_SHIFT_TIME, lastInputtedShiftTime);
        	startActivityForResult(intent, REQUEST_SHIFT_TIME_CALCULATE_FP);
	    	return true;
	    
	    case R.id.save:
	    	mAudioFingerprint.saveRecordedData();
	    	return true;	
	    	
	    case R.id.arguments:
	    	Intent intent2 = new Intent(this, AdhocPairingPreferenceActivity.class);
	    	try {
	    		startActivityForResult(intent2, REQUEST_PREFERENCE_EDIT);
	    	} catch (ActivityNotFoundException e) {
	    		Log.e(TAG, "Activity Not Found Exception");
	    		return false;
	    	}
	    	return true;
	    	
	    case R.id.default_arguments:
	    	setDefaultArguments();
	    	return true;
	    
	    case R.id.sync_pattern:
	    	// Check if there's sample already
	    	if (!mAudioFingerprint.isDataReady()) {
	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
	                    Toast.LENGTH_SHORT).show();
	    		return true;
	    	}
	    	// Call pattern-sync function of AudioFingerprint
	    	mAudioFingerprint.sync_pattern();
	    	return true;
	    	
	    case R.id.commit:
	    	// Check if there's sample already
	    	if (!mAudioFingerprint.isDataReady()) {
	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
	                    Toast.LENGTH_SHORT).show();
	    		return true;
	    	}
	    	// Check debug mode to navigate process
	    	SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
	    	boolean debug_mode = Boolean.parseBoolean(pref.getString("debug_mode", Boolean.toString(DEFAULT_DEBUG_MODE)));
	    	if (debug_mode) {
	    		// Launch ShiftTimeInputActivity to get the shift time
	        	Intent intent3 = new Intent(this, ShiftTimeInput2Activity.class);
	        	intent3.putExtra(ShiftTimeInput2Activity.CURRENT_SHIFT_TIME, lastInputtedShiftTime);
	        	startActivityForResult(intent3, REQUEST_SHIFT_TIME_COMMIT);
	    	} else {
	    		doCommit(false, 0);
	    	}
	    	return true;
	    
	    case R.id.decommit:
	    	// Check if there's sample already
	    	if (!mAudioFingerprint.isDataReady()) {
	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
	                    Toast.LENGTH_SHORT).show();
	    		return true;
	    	}
	    	// Check debug mode to navigate process
	    	SharedPreferences pref2 = PreferenceManager.getDefaultSharedPreferences(this);
	    	boolean debug_mode2 = Boolean.parseBoolean(pref2.getString("debug_mode", Boolean.toString(DEFAULT_DEBUG_MODE)));
	    	if (debug_mode2) {
	    		// Launch ShiftTimeInputActivity to get the shift time
	        	Intent intent3 = new Intent(this, ShiftTimeInput2Activity.class);
	        	intent3.putExtra(ShiftTimeInput2Activity.CURRENT_SHIFT_TIME, lastInputtedShiftTime);
	        	startActivityForResult(intent3, REQUEST_SHIFT_TIME_DECOMMIT);
	    	}
	    	return true;
	    	
	    case R.id.test:
	    	test();
	    	return true;
	    	
	    case R.id.test_decommit:
	    	// Launch the DiffPercentActivity to get different percent 
	        Intent diffIntent = new Intent(this, DiffPercentInputActivity.class);
	        startActivityForResult(diffIntent, REQUEST_DIFF_PERCENT);
	    	return true;
	    
	    case R.id.comparison_table:
	    	// Check if there's sample already
	    	if (!mAudioFingerprint.isDataReady()) {
	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
	                    Toast.LENGTH_SHORT).show();
	    		return true;
	    	}
	    	// Launch DeviceSelector to choose device for comparing
	    	Intent selectorIntent2 = new Intent(this, DeviceSelector.class);
	    	selectorIntent2.putExtra(DeviceSelector.DEVICE_LIST, mConnectedDevicesArrayList);
	    	startActivityForResult(selectorIntent2, REQUEST_DEVICE_FOR_COMPARISON_TABLE);
	    	return true;
	    
	    case R.id.save_ct:
	    	saveComparisonTable();
	    	return true;
	    	
	    case R.id.comparison_table_2:
	    	// Check if there's sample already
	    	if (!mAudioFingerprint.isDataReady()) {
	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
	                    Toast.LENGTH_SHORT).show();
	    		return true;
	    	}
	    	// Launch DeviceSelector to choose device for comparing
	    	Intent selectorIntent3 = new Intent(this, DeviceSelector.class);
	    	selectorIntent3.putExtra(DeviceSelector.DEVICE_LIST, mConnectedDevicesArrayList);
	    	startActivityForResult(selectorIntent3, REQUEST_DEVICE_FOR_COMPARISON_TABLE_2);
	    	return true;
	    	
	    case R.id.comparison_table_3:
	    	// Check if there's sample already
	    	if (!mAudioFingerprint.isDataReady()) {
	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
	                    Toast.LENGTH_SHORT).show();
	    		return true;
	    	}
	    	// Launch DeviceSelector to choose device for comparing
	    	Intent selectorIntent4 = new Intent(this, DeviceSelector.class);
	    	selectorIntent4.putExtra(DeviceSelector.DEVICE_LIST, mConnectedDevicesArrayList);
	    	startActivityForResult(selectorIntent4, REQUEST_DEVICE_FOR_COMPARISON_TABLE_3);
	    	return true;
	    	
	    }
	    return false;
	}

	// Step 1 of finger-print comparison
    private void compare1(BluetoothDevice device, int shift_time, boolean compare_all) {
    	// Order device to send back its finger-print
    	sendTo(COMMAND_SEND_BACK_FINGERPRINT, device);
    	// Calculate this own finger-print
    	if (!compare_all) {
    		if (mAudioFingerprint.getFingerprint(shift_time) == null)
    			mAudioFingerprint.calculateFingerprint(shift_time);
    	}
    }
    
    // Step 2 of finger-print comparison
    private void compare2(BluetoothDevice device, byte[] finger) {
    	if (!compareAllShift) {
	    	int hamming = mAudioFingerprint.calculateHammingDistance(finger, shiftTimeToCompare);
	    	if (hamming == -1)
	    		addStatus(TAG + ": finger-print not calculated, cannot compare");
	    	else
	    		addStatus("Fingerprint Comparison: (device=" + device.getName() 
	    				+ ", shiftTime=" + Integer.toString(shiftTimeToCompare) + ") Hamming Distance = " 
	    				+ Integer.toString(hamming) 
	    				+ " (" + Integer.toString(AudioFingerprint.fingerprintBits) 
	    				+ " bits)\nMatching: " + Integer.toString( (AudioFingerprint.fingerprintBits-hamming)*100/AudioFingerprint.fingerprintBits) + "%");
    	} else {
    		int min = mAudioFingerprint.getMinShiftTime();
    		int max = mAudioFingerprint.getMaxShiftTime();
    		int minHamming = AudioFingerprint.fingerprintBits;
    		int minHammingShift = 0;
    		String s = "";
    		for (int shift=min; shift<=max; shift++) {
    			int hamming = mAudioFingerprint.calculateHammingDistance(finger, shift);
    			if (hamming == -1)
    				s += Integer.toString(shift) + ": -1, ";
    			else {
    				s += Integer.toString(hamming) + ", ";
    				if (hamming < minHamming) {
    					minHamming = hamming;
    					minHammingShift = shift;
    				}
    			}
    		}
    		addStatus(s);
    		addStatus("Min Hamming distance = " + Integer.toString(minHamming) 
    				+ " (" + Integer.toString(AudioFingerprint.fingerprintBits) 
    				+ " bits), achived at shift time = " 
    				+ Integer.toString(minHammingShift)
    				+ "\nRatio: " 
    				+ Integer.toString(Math.round(100*(1-((float) minHamming)/AudioFingerprint.fingerprintBits)))
    				+ "%");
    	}
    }
    
    private void onPreferencesChanged() {
    	SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
    	if (mAudioFingerprint == null) return;
    	mAudioFingerprint.setArguments(
    			Integer.parseInt(pref.getString("sampling_time", Integer.toString(AudioFingerprint.DEFAULT_SAMPLING_TIME))),
    			Integer.parseInt(pref.getString("delayed_time", Integer.toString(AudioFingerprint.DEFAULT_DELAYED_TIME))),
    			Integer.parseInt(pref.getString("redundant_time", Integer.toString(AudioFingerprint.DEFAULT_REDUNDANT_TIME))),
    			Integer.parseInt(pref.getString("sampling_rate", Integer.toString(AudioFingerprint.DEFAULT_SAMPLING_RATE))),
    			Integer.parseInt(pref.getString("frame_length", Integer.toString(AudioFingerprint.DEFAULT_FRAME_LENGTH))),
    			Integer.parseInt(pref.getString("band_length", Integer.toString(AudioFingerprint.DEFAULT_BAND_LENGTH)))
    			);
    	addStatus(mAudioFingerprint.getArgumentsString());
    }
    
    private void ensureDiscoverable() {
	    if(D) Log.d(TAG, "ensureDiscoverable()");
	    if (mBluetoothAdapter.getScanMode() !=
	        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
	        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
	        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
	        startActivity(discoverableIntent);
	    }
	}

    private void sendTo(String s, BluetoothDevice device) {
    	byte[] sdata = s.getBytes();
    	byte[] data = new byte[sdata.length + 1];
    	data[0] = AdhocPairingActivity.COMMAND_RECEIVE_STRING;
    	for (int i=1; i<data.length; i++)
    		data[i] = sdata[i-1];
    	mPairingManager.write(data, device);
    }
    
    private void sendTo(byte command, BluetoothDevice device) {
    	byte[] data = new byte[] {command};
    	mPairingManager.write(data, device);
    }
    
    private void sendTo(byte command, BluetoothDevice device, byte[] d) {
    	int n = d.length;
    	byte[] data = new byte[n+1];
    	data[0] = command;
    	for (int i=1; i<n+1; i++)
    		data[i] = d[i-1];
    	mPairingManager.write(data, device);
    }
    
    private void sendTo(byte command, BluetoothDevice device, int[] delta, int[] plain) {
    	int n = delta.length;
    	int m = plain.length;
    	// Create data array of integers
    	int[] idata = new int[n+m+2];
    	idata[0] = n;
    	idata[1] = m;
    	int k = 2;
    	for (int i=0; i<delta.length; i++)
    		idata[k++] = delta[i];
    	for (int i=0; i<plain.length; i++)
    		idata[k++] = plain[i];
    	// Convert to array of bytes
    	ByteBuffer bytebuffer = ByteBuffer.allocate(idata.length * 4);
    	IntBuffer intbuffer = bytebuffer.asIntBuffer();
    	intbuffer.put(idata);
    	byte[] bdata = bytebuffer.array();
    	// Sending
    	sendTo(command, device, bdata);
    }
    
    private long doSync() {
    	if (mTimer == null) {
    		mTimer = new Timer(this, mHandler);
    		mTimer.start();
    	}
    	if (D) addStatus("Time offset = " + Integer.toString((int) mTimer.getTimeOffset()) + ", avg = " + Integer.toString((int) mTimer.getAverageTimeOffset()));
    	return mTimer.getTimeOffset();
    }
    
    private void setTimeOffset(long l) {
    	this.time_offset = l;
    	addStatus(TAG + ": TimeOffset is set to " + Integer.toString((int) this.time_offset));
    }
    
    public byte[] toBytes(long l) {
    	ByteBuffer buffer = ByteBuffer.allocate(8);
    	buffer.putLong(l);
    	return buffer.array();
    }
    
    public long toLong(byte[] bytearray) {
    	ByteBuffer buffer = ByteBuffer.allocate(8);
    	buffer = (ByteBuffer) buffer.put(bytearray).position(0);
    	long l = buffer.getLong();
    	return l;
    }
    
    private long getSample(long time) {
    	if (mAudioFingerprint != null) {
    		mAudioFingerprint.cancel();
    	} else {
    		mAudioFingerprint = new AudioFingerprint(this, mHandler, mTimer);
    	}
		mAudioFingerprint.initialize();
		addStatus(mAudioFingerprint.getArgumentsString());
		return mAudioFingerprint.startSampling(time);
    }
    
    private void setDefaultArguments() {
    	// Set default preferences
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("sampling_time", Integer.toString(AudioFingerprint.DEFAULT_SAMPLING_TIME)); 
		editor.putString("delayed_time", Integer.toString(AudioFingerprint.DEFAULT_DELAYED_TIME));
		editor.putString("redundant_time", Integer.toString(AudioFingerprint.DEFAULT_REDUNDANT_TIME));
		editor.putString("sampling_rate", Integer.toString(AudioFingerprint.DEFAULT_SAMPLING_RATE));
		editor.putString("frame_length", Integer.toString(AudioFingerprint.DEFAULT_FRAME_LENGTH));
		editor.putString("band_length", Integer.toString(AudioFingerprint.DEFAULT_BAND_LENGTH));
		boolean boo = editor.commit();
		Log.i(TAG, "Result of setting initial arguments is: " + Boolean.toString(boo));
		Toast.makeText(getApplicationContext(), "Result of setting initial arguments is: " + Boolean.toString(boo), Toast.LENGTH_SHORT).show();
    }
    
	// Update title and post NO DEVICE FOUND if necessary
    private void updateTitle(String connect, String audio) {
    	// connect string
    	if (connect != null) {
    		connectTitle = connect;
    	} else {
    		if (mPairingManager.getState() == PairingManager.STATE_LISTEN)
    			connectTitle = getResources().getText(R.string.title_listening).toString();
    		else if (mPairingManager.getState() == PairingManager.STATE_CONNECTING)
    			connectTitle = getResources().getText(R.string.title_connecting).toString();
    		else if (mPairingManager.getState() == PairingManager.STATE_NONE)
    			connectTitle = getResources().getText(R.string.title_not_connected).toString();
    		else if (mPairingManager.getState() == PairingManager.STATE_CONNECTED) {
    			int number = mConnectedDevicesArrayList.size();
    			if (number == 0)
    				connectTitle = Integer.toString(number) + " device connected";
    			else if (number == 1)
    				connectTitle = Integer.toString(number) + " device connected";
    			else
    				connectTitle = Integer.toString(number) + " devices connected";
    		}
    	}
    	
    	// audio string
    	if (audio != null) {
    		audioTitle = audio;
    	} else {
    		if (mAudioFingerprint!=null && mAudioFingerprint.getState()==AudioFingerprint.STATE_RECORDING)
    			audioTitle = getResources().getText(R.string.title_recording).toString();
    		else 
    			audioTitle = "";
    	}
    		
    	String s = connectTitle;
    	if (audioTitle != null && audioTitle != "")
    		s += ", " + audioTitle;
    	mTitle.setText(s);
    }
    
    private void updateTitle() {
    	updateTitle(null, null);
    }
    
    private void addConnectedDevice(BluetoothDevice device) {
    	// Add to ArrayList
    	mConnectedDevicesArrayList.add(device);
    	// Add to ArrayAdapter
    	mConnectedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
    	// Delete the non-device string
    	mConnectedDevicesArrayAdapter.remove(getResources().getText(R.string.none_connected).toString());
    }
    
    private void removeConnectedDevice(BluetoothDevice device) {
    	// Remove from ArrayList
    	mConnectedDevicesArrayList.remove(device);
    	// Remove from ArrayAdapter
    	mConnectedDevicesArrayAdapter.remove(device.getName() + "\n" + device.getAddress());
    	// Add non-device string
    	if (mConnectedDevicesArrayList.size() == 0)
    		mConnectedDevicesArrayAdapter.add(getResources().getText(R.string.none_connected).toString());
    }
    
    private void addStatus(String status) {
    	Date date = new Date(System.currentTimeMillis());
    	SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
    	String time_string = sdf.format(date);
    	mStatusArrayAdapter.add(time_string + "\n" + status);
    }
    
    private void test() {
    	int n, m, symsize;
    	n = AudioFingerprint.fingerprintBits;
		m = (int) Math.round(n - 2*0.4*n);
		symsize = 2;
		for (int i=0; i<ECCoder.defaultRSParamCount; i++)
			if (ECCoder.defaultRSParameters[i][3] > n) {
				symsize = i;
				break;
			}
    	if ( (committer == null) || (committer.getMode() != ECCoder.COMMIT_MODE) ) {
    		committer = new ECCoder(n, m, symsize, ECCoder.COMMIT_MODE, mHandler);
    	}
    	// Get the fingerprint
    	int shifttime = mAudioFingerprint.getPatternMatchingShiftTime();
    	byte[][] fingerprint = mAudioFingerprint.getFingerprint(shifttime);
    	if (fingerprint == null) {
    		mAudioFingerprint.calculateFingerprint(shifttime);
    		addStatus("Fingerprint for commit process not calculated. " +
    				"Plz do commit again after the calculation finish.");
    		Toast.makeText(getApplicationContext(), "Fingerprint is now on calculation.",
                    Toast.LENGTH_SHORT).show();
    		return ;
    	}
    	// Do commit
    	byte[] codewordBytes = new byte[AudioFingerprint.fingerprintBits];
    	int line_leng = fingerprint[0].length;
    	for (int i=0; i<codewordBytes.length; i++)
    		codewordBytes[i] = fingerprint[i/line_leng][i%line_leng];
    	int err = committer.commit(codewordBytes);
    	if (err == -1) {
    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
                    Toast.LENGTH_SHORT).show();
    		return ;
    	}
    	// Select partner to send the plain_word and delta by DeviceSelector
    	//Intent selectorIntent2 = new Intent(this, DeviceSelector.class);
    	//selectorIntent2.putExtra(DeviceSelector.DEVICE_LIST, mConnectedDevicesArrayList);
    	//startActivityForResult(selectorIntent2, REQUEST_DEVICE_TO_COMMIT);
    	
    	// Initialize a decommitter object
    	ECCoder decommitter = new ECCoder(n, m, symsize, ECCoder.DECOMMIT_MODE, mHandler);
    	err = decommitter.putDelta(committer.getDelta());
    	if (err == -1) 
    		this.addStatus("Error from decommitter.putDelta() function.");
    	
    	// Do decommit
    	err = decommitter.decommit(codewordBytes);
    	if (err == -1) {
    		addStatus("Error in decommitter.decommit().");
    		return ;
    	}
    	String str = decommitter.comparePlainWord(committer.getPlainWord());
    	addStatus("Decommit result: " + str);
    }
    
    private void doCommit(boolean debug_mode, int shiftTime) {
    	// If pattern sync is run beforehand, the pattern-sync-position will be used to get the fingerprint
    	// otherwise, default value is 0.
    	// After pattern-sync, should run calculating corresponding fingerprint,
    	// otherwise, it will be called here, after finishing, we have to press Commit again.
    	
    	// Create committer if not exist
    	if ( (committer == null) || (committer.getMode() != ECCoder.COMMIT_MODE) ) {
    		int n = AudioFingerprint.fingerprintBits;
    		int m = (int) Math.round(n - 2*DIFF_LIMIT*n);
    		int symsize = 2;
    		for (int i=0; i<ECCoder.defaultRSParamCount; i++)
    			if (ECCoder.defaultRSParameters[i][3] > n) {
    				symsize = i;
    				break;
    			}
    		committer = new ECCoder(n, m, symsize, ECCoder.COMMIT_MODE, mHandler);
    	}
    	// Get the fingerprint
    	int shifttime;
    	if (!debug_mode)
    		shifttime = mAudioFingerprint.getPatternMatchingShiftTime();
    	else
    		shifttime = shiftTime;
    	byte[][] fingerprint = mAudioFingerprint.getFingerprint(shifttime);
    	if (fingerprint == null) {
    		mAudioFingerprint.calculateFingerprint(shifttime);
    		addStatus("Fingerprint for commit process not calculated. " +
    				"Plz do commit again after the calculation finish.");
    		Toast.makeText(getApplicationContext(), "Fingerprint is now on calculation.",
                    Toast.LENGTH_SHORT).show();
    		return ;
    	}
    	// Do commit
    	byte[] codewordBytes = new byte[AudioFingerprint.fingerprintBits];
    	int line_leng = fingerprint[0].length;
    	for (int i=0; i<codewordBytes.length; i++)
    		codewordBytes[i] = fingerprint[i/line_leng][i%line_leng];
    	int err = committer.commit(codewordBytes);
    	if (err == -1) {
    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
                    Toast.LENGTH_SHORT).show();
    		return ;
    	}
    	// Select partner to send the plain_word and delta by DeviceSelector
    	Intent selectorIntent2 = new Intent(this, DeviceSelector.class);
    	selectorIntent2.putExtra(DeviceSelector.DEVICE_LIST, mConnectedDevicesArrayList);
    	startActivityForResult(selectorIntent2, REQUEST_DEVICE_TO_COMMIT);
    }
    
    private void doDecommit(int n, int m, int[] delta, int[] plain) {
    	// Check if there's sample already
    	if (!mAudioFingerprint.isDataReady()) {
    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
                    Toast.LENGTH_SHORT).show();
    		return;
    	}
    	
    	// Initialize a decommitter object
    	int symsize = 2;
    	for (int i=0; i<ECCoder.defaultRSParamCount; i++)
			if (ECCoder.defaultRSParameters[i][3] > n) {
				symsize = i;
				break;
			}
    	decommitter = new ECCoder(n, m, symsize, ECCoder.DECOMMIT_MODE, mHandler);
    	int err;
    	err = decommitter.putDelta(delta);
    	if (err == -1) 
    		this.addStatus("Error from decommitter.putDelta() function.");
    	
    	// Initialize myECCoder object in order to test Decommit process
    	myECCoder = new ECCoder(n, m, symsize, ECCoder.TEST_DECOMMIT_MODE, mHandler);
    	err = myECCoder.putPlainDelta(plain, delta);
    	if (err == -1)
    		this.addStatus("Error from myECCoder.putPlainDelta() function");
    	
    	// Call next step of doDecommit function
    	doDecommit2(false, 0);
    }
    
    private void doDecommit2(boolean debug_mode, int shiftTime) {
    	// Check if there is decommitter object
    	if (decommitter == null) {
    		addStatus("Error: There is no decommitter object.");
    		return;
    	}
    	
    	// Get the fingerprint
    	int shifttime;
    	if (!debug_mode)
    		shifttime = mAudioFingerprint.getPatternMatchingShiftTime();
    	else
    		shifttime = shiftTime;
    	byte[][] fingerprint = mAudioFingerprint.getFingerprint(shifttime);
    	if (fingerprint == null) {
    		mAudioFingerprint.calculateFingerprint(shifttime);
    		addStatus("Fingerprint for decommit process not calculated. " +
    				"Plz do commit again after the calculation finish.");
    		Toast.makeText(getApplicationContext(), "Fingerprint is now on calculation.",
                    Toast.LENGTH_SHORT).show();
    		return ;
    	}
    	// Convert the fingerprint
    	byte[] codewordBytes = new byte[AudioFingerprint.fingerprintBits];
    	int line_leng = fingerprint[0].length;
    	for (int i=0; i<codewordBytes.length; i++)
    		codewordBytes[i] = fingerprint[i/line_leng][i%line_leng];
    	// Do decommit
    	int err;
    	err = decommitter.decommit(codewordBytes);
    	if (err == -1) {
    		addStatus("Error in decommitter.decommit().");
    		return ;
    	}
    	String str = decommitter.comparePlainWord(myECCoder.getPlainWord());
    	addStatus("Decommit result: " + str);
    }
    
    private void testDoDecommit(int diff_percent) {
    	// Check if there is myECCoder object
    	if (myECCoder == null) {
    		addStatus("Error: There is no data to test Decommit.");
    		return;
    	}
    	
    	// Get initial parameter data
    	int n = myECCoder.getN();
    	int m = myECCoder.getM();
    	int symsize = myECCoder.getSymsize();
    	
    	// Initialize a decommitter object
    	ECCoder decommitter = new ECCoder(n, m, symsize, ECCoder.DECOMMIT_MODE, mHandler);
    	int err;
    	err = decommitter.putDelta(myECCoder.getDelta());
    	if (err == -1) 
    		addStatus("Error from decommitter.putDelta() function.");
    	
    	// Copy the original fingerprint
    	byte[] codewordBytes = myECCoder.getFinger().clone();
    	
    	// Check finger before editing
    	addStatus("Compare fingerprints before editing\n" + 
    				myECCoder.compareFinger(codewordBytes));
    	
    	// Edit the fingerprint for it to be different diff_percent percents
    	int length = codewordBytes.length;
    	int numErr = length * diff_percent / 100;
    	int step = (int) (length * 0.8 / numErr);
    	int initial = 0;
    	for (int i=initial; i<numErr*step; i+=step) {
    		codewordBytes[i] = (byte) (1 - codewordBytes[i]);
    	}
    	
    	// Post info of the finger-print to be decommitted
    	String compare = myECCoder.compareFinger(codewordBytes);
    	addStatus("Compare fingerprints with diff_percent = " + Integer.toString(diff_percent) + 
    				"%\n" + compare);
    	
    	// Do decommit
    	err = decommitter.decommit(codewordBytes);
    	if (err == -1) {
    		addStatus("Error in decommitter.decommit().");
    		return ;
    	}
    	String str = decommitter.comparePlainWord(myECCoder.getPlainWord());
    	addStatus("Decommit result: " + str);
    } 
    
    // Send FP for creating Comparison Table
    private void sendFP_CT() {
    	// Check if the FP exists
    	int shiftTime = mAudioFingerprint.getPatternMatchingShiftTime(CT_currentMatchingPos);
		if (mAudioFingerprint.getFingerprint(shiftTime) == null) {
			CT_waitingFP = true;
			mAudioFingerprint.calculateFingerprint(shiftTime);
			return;
		}
		// Send FP data
		byte[][] finger = mAudioFingerprint.getFingerprint(shiftTime);
    	if (finger == null) 
    		sendTo("Cannot extract finger-print", CT_receiver);
    	else {
    		int n = finger.length;
    		int m = finger[0].length;
    		byte[] send = new byte[n*m];
    		for (int i=0; i<n; i++)
    			for (int j=0; j<m; j++)
    				send[i*m + j] = finger[i][j];
    		sendTo(COMMAND_CREATE_COMPARISON_TABLE, CT_receiver, send);
    	}
    }
    
    // Send FP for creating Comparison Table 2
    private void sendFP_CT2() {
    	// Check if the FP exists
    	int shiftTime = 0;
		if (mAudioFingerprint.getFingerprint(shiftTime) == null) {
			CT2_result_waitingFP = true;
			mAudioFingerprint.calculateFingerprint(shiftTime);
			return;
		}
		// Send FP data
		byte[][] finger = mAudioFingerprint.getFingerprint(shiftTime);
    	if (finger == null) 
    		sendTo("Cannot extract finger-print", CT2_receiver);
    	else {
    		int n = finger.length;
    		int m = finger[0].length;
    		byte[] send = new byte[n*m];
    		for (int i=0; i<n; i++)
    			for (int j=0; j<m; j++)
    				send[i*m + j] = finger[i][j];
    		sendTo(COMMAND_CREATE_COMPARISON_TABLE_2, CT2_receiver, send);
    	}
    }
    
    // Send FP for creating Comparison Table 3
    private void sendFP_CT3() {
    	// Check if the FP exists
    	int shiftTime = mAudioFingerprint.getRandomShiftTime(CT3_currentMatchingPos);
		if (mAudioFingerprint.getFingerprint(shiftTime) == null) {
			CT3_waitingFP = true;
			mAudioFingerprint.calculateFingerprint(shiftTime);
			return;
		}
		// Send FP data
		byte[][] finger = mAudioFingerprint.getFingerprint(shiftTime);
    	if (finger == null) 
    		sendTo("Cannot extract finger-print", CT3_receiver);
    	else {
    		int n = finger.length;
    		int m = finger[0].length;
    		byte[] send = new byte[n*m];
    		for (int i=0; i<n; i++)
    			for (int j=0; j<m; j++)
    				send[i*m + j] = finger[i][j];
    		sendTo(COMMAND_CREATE_COMPARISON_TABLE_3, CT3_receiver, send);
    	}
    }
    
    // Compare received FP with internal FPs
    private void create_CT() {
    	// Check if the FP exists
    	int shiftTime = mAudioFingerprint.getPatternMatchingShiftTime(CT_currentPattern);
		if (mAudioFingerprint.getFingerprint(shiftTime) == null) {
			CT_waitingFP2 = true;
			mAudioFingerprint.calculateFingerprint(shiftTime);
			return;
		}
		// Compare and store results
    	int hamming = mAudioFingerprint.calculateHammingDistance(CT_transmitter_finger, shiftTime);
    	if (hamming == -1)
    		addStatus("create_CT(): finger-print not calculated, cannot compare");
    	else {
    		CT_result[CT_currentTrial][CT_currentPattern] = (AudioFingerprint.fingerprintBits - hamming) * 100 / AudioFingerprint.fingerprintBits;
    		String str = "Trial=" + Integer.toString(CT_currentTrial) + ", Index="
    					+ Integer.toString(CT_currentPattern) + ": " + Integer.toString(CT_result[CT_currentTrial][CT_currentPattern]);
    		addStatus(str);
    	}
    	CT_currentPattern++;
    	if (CT_currentPattern < AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS)
    		create_CT();
    	else {
    		if (CT_currentTrial < AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS - 1)
    			sendTo(COMMAND_SEND_NEXT_FINGERPRINT, CT_transmitter);
    		else {
    			// Reset 
    			CT_onProgress = false;
    			// Output the Comparison Table
    			int n = AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS;
    			String str;
    			for (int i=0; i<n; i++) {
    				str = Integer.toString(i) + ": ";
    				for (int j=0; j<n; j++) {
    					str += Integer.toString(CT_result[i][j]);
    					if (j<n-1) str += ", ";
    				}
    				addStatus(str);
    			}
    		}
    	}
    }
    
    // Compare received FP with internal FPs, shift around (instead of pattern matching)
    private void create_CT2() {
    	// Check if the FP exists
    	int shiftTime = CT2_result_shifttable[CT2_result_currentshift];
		if (mAudioFingerprint.getFingerprint(shiftTime) == null) {
			CT2_result_waitingFP2 = true;
			mAudioFingerprint.calculateFingerprint(shiftTime);
			return;
		}
		// Compare and store results
    	int hamming = mAudioFingerprint.calculateHammingDistance(CT2_transmitter_finger, shiftTime);
    	if (hamming == -1)
    		addStatus("create_CT2(): finger-print not calculated, cannot compare");
    	else {
    		CT2_result[CT2_result_currentshift] = (AudioFingerprint.fingerprintBits - hamming) * 100 / AudioFingerprint.fingerprintBits;
    		String str = "CT2_result_currentshift=" + Integer.toString(CT2_result_currentshift) + " ("
    					+ Integer.toString(CT2_result_shifttable[CT2_result_currentshift]) + "): " + Integer.toString(CT2_result[CT2_result_currentshift]) + "%";
    		addStatus(str);
    	}
    	CT2_result_currentshift++;
    	if (CT2_result_currentshift < CT2_result_shifttable.length)
    		create_CT2();
    	else {
    			// Output the Comparison Table
    			int n = CT2_result_shifttable.length;
    			String str = "";
    			for (int i=0; i<n; i++) {
    				str += Integer.toString(CT2_result_shifttable[i]) + ": ";
    				str += Integer.toString(CT2_result[i]) + "%";
    				if (i<n-1) str += ", ";
    			}
    			addStatus(str);
    	}
    }
    
 // Compare received FP with internal FPs
    private void create_CT3() {
    	// Check if the FP exists
    	int shiftTime = mAudioFingerprint.getRandomShiftTime(CT3_currentPattern);
		if (mAudioFingerprint.getFingerprint(shiftTime) == null) {
			CT3_waitingFP2 = true;
			mAudioFingerprint.calculateFingerprint(shiftTime);
			return;
		}
		// Compare and store results
    	int hamming = mAudioFingerprint.calculateHammingDistance(CT3_transmitter_finger, shiftTime);
    	if (hamming == -1)
    		addStatus("create_CT3(): finger-print not calculated, cannot compare");
    	else {
    		CT3_result[CT3_currentTrial][CT3_currentPattern] = (AudioFingerprint.fingerprintBits - hamming) * 100 / AudioFingerprint.fingerprintBits;
    		String str = "Trial=" + Integer.toString(CT3_currentTrial) + ", Index="
    					+ Integer.toString(CT3_currentPattern) + ": " + Integer.toString(CT3_result[CT3_currentTrial][CT3_currentPattern]);
    		addStatus(str);
    	}
    	CT3_currentPattern++;
    	if (CT3_currentPattern < AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS)
    		create_CT3();
    	else {
    		if (CT3_currentTrial < AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS - 1)
    			sendTo(COMMAND_SEND_NEXT_FINGERPRINT_CT3, CT3_transmitter);
    		else {
    			// Reset 
    			CT3_onProgress = false;
    			// Output the Comparison Table
    			int n = AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS;
    			String str;
    			for (int i=0; i<n; i++) {
    				str = Integer.toString(i) + ": ";
    				for (int j=0; j<n; j++) {
    					str += Integer.toString(CT3_result[i][j]);
    					if (j<n-1) str += ", ";
    				}
    				addStatus(str);
    			}
    		}
    	}
    }
    
    private void saveComparisonTable() {
    	// Create file name
		Date date = new Date();
    	SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
    	String time_string = sdf.format(date);
		String filename = "ComparisonTable_" + time_string + ".txt";
		
		try {
			//FileWriter f = new FileWriter("/sdcard/AdhocPairing/" + filename);
			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			FileWriter f = new FileWriter(path + "/AdhocPairing/" + filename);
			int n = AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS;
			// 1st			
			for (int i=0; i<n; i++) {
				f.write(Integer.toString(i+1) + ", ");
				for (int j=0; j<n; j++) {
					f.write(Integer.toString(CT_result[i][j]));
					if (j<n-1)
						f.write(", ");
				}
				f.write("\n");
			}
			f.write("\n");
			// 2st
			for (int i=0; i<n; i++)
				for (int j=0; j<n; j++)
					CT_result[i][j] = 100 - CT_result[i][j];
			for (int i=0; i<n; i++) {
				f.write(Integer.toString(i+1) + ", ");
				for (int j=0; j<n; j++) {
					f.write(Integer.toString(CT_result[i][j]));
					if (j<n-1)
						f.write(", ");
				}
				f.write("\n");
			}
			f.write("\n");
			// 3st
			for (int i=0; i<n; i++)
				for (int j=0; j<n; j++) {
					if (i==0 && j==0)
						CT_result[i][j] = CT_result[i][j];
					else if (i==0)
						CT_result[i][j] = Math.min(CT_result[i][j], CT_result[i][j-1]);
					else if (j==0)
						CT_result[i][j] = Math.min(CT_result[i][j], CT_result[i-1][j]);
					else 
						CT_result[i][j] = Math.min(CT_result[i][j], Math.min(CT_result[i-1][j], CT_result[i][j-1]));
				}
			for (int i=0; i<n; i++) {
				f.write(Integer.toString(i+1) + ", ");
				for (int j=0; j<n; j++) {
					f.write(Integer.toString(CT_result[i][j]));
					if (j<n-1)
						f.write(", ");
				}
				f.write("\n");
			}
			
			// CT3
			f.write("\n\n\n CT3 (random postisions):\n");
			// CT3 1st			
			for (int i=0; i<n; i++) {
				f.write(Integer.toString(i+1) + ", ");
				for (int j=0; j<n; j++) {
					f.write(Integer.toString(CT3_result[i][j]));
					if (j<n-1)
						f.write(", ");
				}
				f.write("\n");
			}
			f.write("\n");
			// CT3 2st
			for (int i=0; i<n; i++)
				for (int j=0; j<n; j++)
					CT3_result[i][j] = 100 - CT3_result[i][j];
			for (int i=0; i<n; i++) {
				f.write(Integer.toString(i+1) + ", ");
				for (int j=0; j<n; j++) {
					f.write(Integer.toString(CT3_result[i][j]));
					if (j<n-1)
						f.write(", ");
				}
				f.write("\n");
			}
			f.write("\n");
			// CT3 3st
			for (int i=0; i<n; i++)
				for (int j=0; j<n; j++) {
					if (i==0 && j==0)
						CT3_result[i][j] = CT3_result[i][j];
					else if (i==0)
						CT3_result[i][j] = Math.min(CT3_result[i][j], CT3_result[i][j-1]);
					else if (j==0)
						CT3_result[i][j] = Math.min(CT3_result[i][j], CT3_result[i-1][j]);
					else 
						CT3_result[i][j] = Math.min(CT3_result[i][j], Math.min(CT3_result[i-1][j], CT3_result[i][j-1]));
				}
			for (int i=0; i<n; i++) {
				f.write(Integer.toString(i+1) + ", ");
				for (int j=0; j<n; j++) {
					f.write(Integer.toString(CT3_result[i][j]));
					if (j<n-1)
						f.write(", ");
				}
				f.write("\n");
			}
			
			// CT2
			f.write("\n\n\n CT2 (shift left/right):\n");
			int m = CT2_result_shifttable.length;
			for (int i=0; i<m; i++) {
				f.write(Integer.toString(CT2_result_shifttable[i]) + " ");
			}
			f.write("\n");
			for (int i=0; i<m; i++) {
				f.write(Integer.toString(CT2_result[i]) + "% ");
			}

			f.close();
		} catch(IOException e) {
			addStatus("Cannot open file to write.");
			return ;
		}
		addStatus("Data saved successfully.\n" + filename);
    }
    
    // The Handler that gets information back from the PairingManager
    private final Handler mHandler = new Handler() {
    	
        @Override
        public void handleMessage(Message msg) {
        	BluetoothDevice device;
        	
            switch (msg.what) {
            
            case MESSAGE_ADD_DEVICE:
            	device = (BluetoothDevice) msg.obj;
            	addConnectedDevice(device);
            	// Update title
            	updateTitle();
            	// Toasting
            	Toast.makeText(getApplicationContext(), "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
            	break;
            	
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case PairingManager.STATE_CONNECTED:
                	updateTitle();
                    break;
                case PairingManager.STATE_CONNECTING:
                	updateTitle();
                    break;
                case PairingManager.STATE_LISTEN:
                	updateTitle();
                	break;
                case PairingManager.STATE_NONE:
                	updateTitle();
                    break;
                }
                break;
                
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
                
            case MESSAGE_POST:
            	String status = msg.getData().getString(STATUS);
            	addStatus(status);
            	break;
            	
            case MESSAGE_RECEIVE_COMMAND:
            	byte command = (byte) msg.arg1;
            	device = (BluetoothDevice) msg.obj;
                // Post status informing message receive
                addStatus("Handler: Received command code " + Integer.toString(command) + " from " + device.getName());
  
                switch (command) {
                
                case COMMAND_SYNC:
                	long l = doSync();
                	setTimeOffset(l);
                	// Send back it's time offset
                	String s = "Time offset = " + Integer.toString((int) l);
                	sendTo(s, device);
                	break;
                	
                case COMMAND_GET_SAMPLE:
                	byte[] buffer = msg.getData().getByteArray(BYTE_ARRAY);
                	long time = toLong(buffer); //atomic time-line
                	if (D) addStatus(TAG + ": (atomic) time to sample = " + Long.toString(time));
                	
                	// Back up the recording atomic time
                	mAudioFingerprint.setAtomicRecordingTime(time);
                	
                	time -= mTimer.getTimeOffset(); //local time-line
                	getSample(time);
                	break;
                	
                case COMMAND_SEND_BACK_FINGERPRINT:
                	// Check if there's sample already
        	    	if (!mAudioFingerprint.isDataReady()) {
        	    		Toast.makeText(getApplicationContext(), "There is not recorded audio data yet",
        	                    Toast.LENGTH_SHORT).show();
        	    		break;
        	    	}
        	    	// Set waitingDevice in advance
        	    	waitingDevice = device;
                	// Launch ShiftTimeInput2Activity to get the shift time
    	        	Intent intent = new Intent(AdhocPairingActivity.this, ShiftTimeInput2Activity.class);
    	        	intent.putExtra(ShiftTimeInput2Activity.CURRENT_SHIFT_TIME, lastInputtedShiftTime);
    	        	startActivityForResult(intent, REQUEST_SHIFT_TIME_SENDBACK_FP);
                	break;
                	
                case COMMAND_RECEIVE_MY_FINGERPRINT:
                	addStatus(TAG + ": Received finger-print from " + device.getName());
                	byte[] receivedFinger = msg.getData().getByteArray(BYTE_ARRAY);
                	compare2(device, receivedFinger);
                	break;
                	
                case COMMAND_RECEIVE_COMMIT:
                	addStatus(TAG + ": Received request to do decommit from " + device.getName());
                	byte[] bdata = msg.getData().getByteArray(BYTE_ARRAY);
                	
                	// Convert byte array into int array
                    int size = (bdata.length / 4) + ((bdata.length % 4 == 0) ? 0 : 1);      
                    ByteBuffer bytebuffer = ByteBuffer.allocate(size * 4); 
                    bytebuffer.put(bdata);
                    //Java uses Big Endian. Network program uses Little Endian. 
                    //bytebuffer.order(ByteOrder.LITTLE_ENDIAN); 
                    bytebuffer.rewind(); 
                    IntBuffer intbuffer =  bytebuffer.asIntBuffer();         
                    int[] idata = new int[size]; 
                    intbuffer.get(idata);
                    
                    // Get commit data
                    int n = idata[0];
                    int m = idata[1];
                    if (n+m+2 > idata.length){
                    	addStatus(TAG + "[ERROR] n+m+2=" + Integer.toString(n+m+2) + " != " 
                    				+ "idata.length=" + Integer.toString(idata.length));
                    	mPairingManager.requireMoreCommitData(device, n, m);
                    	break;
                    }
                    int k = 2;
                    int[] delta = new int[n];
                    for (int i=0; i<n; i++)
                    	delta[i] = idata[k++];
                    int[] plain = new int[m];
                    for (int i=0; i<m; i++)
                    	plain[i] = idata[k++];
                    doDecommit(n, m, delta, plain);
                	break;
                	
                case COMMAND_CREATE_COMPARISON_TABLE:
                	if (!CT_onProgress) {
                		CT_onProgress = true;
                		CT_currentTrial = 0;
                		CT_currentPattern = 0;
                		addStatus("Going to create Comparison Table with " + device.getName());
                	} else {
                		CT_currentTrial++;
                		CT_currentPattern = 0;
                	}
                	CT_transmitter = device;
                	CT_transmitter_finger = msg.getData().getByteArray(BYTE_ARRAY);
                	create_CT();
                	break;
                	
                case COMMAND_SEND_NEXT_FINGERPRINT:
                	CT_currentMatchingPos++;
                	if (CT_currentMatchingPos < AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS)
                		sendFP_CT();
                	break;
                	
                case COMMAND_SEND_NEXT_FINGERPRINT_CT3:
                	CT3_currentMatchingPos++;
                	if (CT3_currentMatchingPos < AudioFingerprint.NUMBER_OF_MATCHING_POSITIONS)
                		sendFP_CT3();
                	break;
                
                case COMMAND_CREATE_COMPARISON_TABLE_2:
                	addStatus("Going to create Comparison Table 2 with " + device.getName());
                	CT2_transmitter = device;
                	CT2_transmitter_finger = msg.getData().getByteArray(BYTE_ARRAY);
                	create_CT2();
                	break;
                	
                case COMMAND_CREATE_COMPARISON_TABLE_3:
                	if (!CT3_onProgress) {
                		CT3_onProgress = true;
                		CT3_currentTrial = 0;
                		CT3_currentPattern = 0;
                		mAudioFingerprint.generateRandomShiftTime();
                		addStatus("Going to create Comparison Table 3 with " + device.getName());
                	} else {
                		CT3_currentTrial++;
                		CT3_currentPattern = 0;
                	}
                	CT3_transmitter = device;
                	CT3_transmitter_finger = msg.getData().getByteArray(BYTE_ARRAY);
                	create_CT3();
                	break;
                	
                } // switch
                break; // case MESSAGE_RECEIVE_COMMAND
                
            case MESSAGE_DEVICE_LOST:
            	device = (BluetoothDevice) msg.obj;
            	removeConnectedDevice(device);
            	updateTitle();
            	Toast.makeText(getApplicationContext(), "Lost connection to " + device.getName(), Toast.LENGTH_SHORT).show();
            	break;
                
            case MESSAGE_RECORDING_STATE_CHANGE:
            	String s = (String) msg.obj;
            	updateTitle(null, s);
                break;
                
            case MESSAGE_FINISH_CALCULATING_FP:
            	int minShiftTime = msg.arg1;
            	int maxShiftTime = msg.arg2;
            	if (minShiftTime != maxShiftTime) 
            		addStatus("Calculated finger-print of shift_time=[" 
            				+ Integer.toString(minShiftTime) + "->" + Integer.toString(maxShiftTime) + "]");
            	else
            		addStatus("Calculated finger-print of shift_time=" 
            				+ Integer.toString(minShiftTime));
            	if (isWaitingForCalculatingFP) {
            		byte[][] finger = mAudioFingerprint.getFingerprint(minShiftTime);
                	if (finger == null) 
                		sendTo("Cannot extract finger-print", waitingDevice);
                	else {
                		int n = finger.length;
                		int m = finger[0].length;
                		byte[] send = new byte[n*m];
                		for (int i=0; i<n; i++)
                			for (int j=0; j<m; j++)
                				send[i*m + j] = finger[i][j];
                		sendTo(COMMAND_RECEIVE_MY_FINGERPRINT, waitingDevice, send);
                	}
                	isWaitingForCalculatingFP = false;
                	waitingDevice = null;
            	}
            	// CT
            	if (CT_waitingFP) {
            		CT_waitingFP = false;
            		sendFP_CT();
            	}
            	if (CT_waitingFP2) {
            		CT_waitingFP2 = false;
            		create_CT();
            	}
            	// CT2
            	if (CT2_result_waitingFP) {
            		CT2_result_waitingFP = false;
            		sendFP_CT2();
            	}
            	if (CT2_result_waitingFP2) {
            		CT2_result_waitingFP2 = false;
            		create_CT2();
            	}
            	// CT3
            	if (CT3_waitingFP) {
            		CT3_waitingFP = false;
            		sendFP_CT3();
            	}
            	if (CT3_waitingFP2) {
            		CT3_waitingFP2 = false;
            		create_CT3();
            	}
            	break;
                
            }
            
        }
       
    };
    
}