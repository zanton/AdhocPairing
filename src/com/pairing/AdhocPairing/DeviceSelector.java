package com.pairing.AdhocPairing;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class DeviceSelector extends Activity {
	// Debugging
    private static final String TAG = "DeviceSelector";
    private static final boolean D = false;
    
    // Key string for getting data in Intent object
	public static final String DEVICE_LIST = "device_list";
	public static final String DEVICE_OBJECT = "device_object";

	// Member fields
	private ArrayAdapter<String> mArrayAdapter;
	ArrayList<BluetoothDevice> mArrayList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_selector);

        // Set result CANCELED incase the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        mArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        // Find and set up the ListView for paired devices
        ListView mListView = (ListView) findViewById(R.id.choices_list);
        mListView.setAdapter(mArrayAdapter);
        mListView.setOnItemClickListener(mDeviceClickListener);

        // Add currently connected devices to the ArrayAdapter
        Intent mIntent = getIntent();
        mArrayList = mIntent.getParcelableArrayListExtra(DEVICE_LIST);
        for (BluetoothDevice device : mArrayList) {
        	mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
        }
        if (mArrayAdapter.getCount() == 0)
        	mArrayAdapter.add(getResources().getText(R.string.none_connected).toString());
    }

    @Override
    protected void onDestroy() {
    	Log.i(TAG, "onDestroy()");
        super.onDestroy();
    }
    
    // The on-click listener for all devices in the ListViews
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
    	
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // Find the clicked device
            BluetoothDevice device = null;
            for (BluetoothDevice d : mArrayList)
            	if (d.getAddress().compareTo(address) == 0) {
            		device = d;
            		break;
            	}
            
            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra(DEVICE_OBJECT, device);

            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            finish();
            
        }
    };
}
