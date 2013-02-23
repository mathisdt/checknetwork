package org.zephyrsoft.checknetwork;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import org.zephyrsoft.checknetwork.util.Logger;

/**
 * The main screen of the application.
 * 
 * @author Mathis Dirksen-Thedens
 */
public class MainActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.options);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		Logger.debug("detected change of preference \"{0}\"", key);
		PeriodicCheckingManager.enableOrDisablePeriodicChecking(getApplicationContext());
	}
	
}
