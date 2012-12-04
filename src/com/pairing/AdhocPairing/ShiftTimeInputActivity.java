package com.pairing.AdhocPairing;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class ShiftTimeInputActivity extends Activity {
	// Debugging
    private static final String TAG = "ShiftTimeInputActivity";
    private static final boolean D = false;
    
    // Key string for getting data in Intent object
	public static final String SHIFT_TIME = "shift_time";
	public static final String CURRENT_SHIFT_TIME = "current_shift_time";
	public static final String ALL_SHIFT_TIME = "all_shift_time";
	
	// Layout objects
	EditText mEditText;
	Button mButtonOk;
	Button mButtonCancel;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
    	Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.shift_time_inputer);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Get the EditText
        mEditText = (EditText) findViewById(R.id.shift_time_edittext);
        
        // Set text for the EditText
        Intent intent = getIntent();
        mEditText.setText(Integer.toString(intent.getIntExtra(CURRENT_SHIFT_TIME, 0)));

        // Add listeners to 2 buttons
        mButtonOk = (Button) findViewById(R.id.button_ok);
        mButtonCancel = (Button) findViewById(R.id.button_cancel);
        mButtonOk.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View view) {
        		// Get the shift time from EditText
        		String s = mEditText.getText().toString();
        		int i = 0;
        		try {
        			i = Integer.parseInt(s);
        		} catch (NumberFormatException e) {
        			Toast.makeText(getApplicationContext(), "Shift Time Input Error", Toast.LENGTH_SHORT).show();
        			return ;
        		}
        		
        		// Get the status of check-box
        		CheckBox checkbox = (CheckBox) findViewById(R.id.shift_time_checkbox);
        		boolean all_shift = checkbox.isChecked();
        		
        		// Create the result Intent and include shift time
                Intent intent = new Intent();
        		if (all_shift) {
        			intent.putExtra(SHIFT_TIME, i);
	                intent.putExtra(ALL_SHIFT_TIME, true);
        		} else if (checkShiftTime(i)) {
	                intent.putExtra(SHIFT_TIME, i);
	                intent.putExtra(ALL_SHIFT_TIME, false);
        		} else {
        			Toast.makeText(getApplicationContext(), "Shift time is invalid", Toast.LENGTH_SHORT).show();
        			setResult(Activity.RESULT_CANCELED);
                    finish();
        		}
        		// Set result and finish this Activity
                setResult(Activity.RESULT_OK, intent);
                finish();
        	}
        	
        	boolean checkShiftTime(int time) {
        		return true;
        	}
        });
        mButtonCancel.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View view) {
        		setResult(Activity.RESULT_CANCELED);
            	finish();
        	}
        });
    }

    @Override
    protected void onDestroy() {
    	Log.i(TAG, "onDestroy()");
        super.onDestroy();
    }
    
}
