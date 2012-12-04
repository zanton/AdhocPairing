package com.pairing.AdhocPairing;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class AdhocPairingPreferenceActivity extends PreferenceActivity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			addPreferencesFromResource(R.xml.preference);
		} catch (Exception e) {
			String s = e.getMessage();
		}
		setResult(Activity.RESULT_OK);
	}
	
}
