package com.pairing.AdhocPairing;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class DiffPercentInputActivity extends Activity {
	// Debugging
    private static final String TAG = "DiffPercentInputActivity";
    private static final boolean D = false;
    
    // Key string for getting data in Intent object
	public static final String DIFF_PERCENT = "diff_percent";
	
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
        setContentView(R.layout.diff_percent_inputer);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Get the EditText
        mEditText = (EditText) findViewById(R.id.diff_percent_edittext);
        
        // Set text for the EditText
        mEditText.setText("10");

        // Add listeners to 2 buttons
        mButtonOk = (Button) findViewById(R.id.button_ok);
        mButtonCancel = (Button) findViewById(R.id.button_cancel);
        mButtonOk.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View view) {
        		// Get the diff percent from EditText
        		String s = mEditText.getText().toString();
        		int i = 0;
        		try {
        			i = Integer.parseInt(s);
        		} catch (NumberFormatException e) {
        			Toast.makeText(getApplicationContext(), "Shift Time Input Error", Toast.LENGTH_SHORT).show();
        			return ;
        		}
        		
        		// Create the result Intent and include diff percent
                Intent intent = new Intent();
                if (i>=0 && i<=100) {
                	intent.putExtra(DIFF_PERCENT, i);
        		} else {
        			Toast.makeText(getApplicationContext(), "Diff percent is invalid", Toast.LENGTH_SHORT).show();
        			setResult(Activity.RESULT_CANCELED);
                    finish();
        		}
        		// Set result and finish this Activity
                setResult(Activity.RESULT_OK, intent);
                finish();
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
