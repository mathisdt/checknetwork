package org.zephyrsoft.checknetwork;

import java.net.HttpURLConnection;
import java.net.URL;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import org.zephyrsoft.checknetwork.util.Logger;

/**
 * Checks if the device has internet connectivity.
 * 
 * @author Mathis Dirksen-Thedens
 */
public class ConnectivityChecker extends BroadcastReceiver {
	
	private static final int MILLIS_TO_SLEEP = 5 * 1000;
	
	private static final Uri APN_TABLE_URI = Uri.parse("content://telephony/carriers");
	private static final Uri PREFERRED_APN_URI = Uri.parse("content://telephony/carriers/preferapn");
	
	@Override
	public void onReceive(final Context context, Intent intent) {
		AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				Logger.info("checking connectivity");
				
				ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo netInfo = cm.getActiveNetworkInfo();
				String networkType = "unknown";
				if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
					networkType = "wifi";
				} else if (netInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
					networkType = "mobile data";
				}
				boolean connectionOK = (netInfo != null && netInfo.isConnected());
				if (connectionOK) {
					// try to connect to a host (to test if the connection really works)
					HttpURLConnection connection = null;
					try {
						URL url = new URL("http://www.google.com");
						connection = (HttpURLConnection) url.openConnection();
						connection.setConnectTimeout(3000);
						connection.connect();
						if (connection.getResponseCode() == 200) {
							connectionOK = true;
						}
					} catch (Exception e) {
						connectionOK = false;
					} finally {
						if (connection != null) {
							connection.disconnect();
						}
					}
				}
				// take measures if connection failed
				if (!connectionOK) {
					Logger.debug("connection not working, type={0}", networkType);
					tryToReconnect(context, cm);
				} else {
					Logger.debug("connection OK, type={0}", networkType);
				}
				return null;
			}
		};
		task.execute();
	}
	
	private void tryToReconnect(Context context, ConnectivityManager cm) {
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
			Logger.info("toggling wifi connection");
			setWifiEnabled(context, false);
			try {
				Thread.sleep(MILLIS_TO_SLEEP);
			} catch (InterruptedException e) {
				// do nothing
			}
			setWifiEnabled(context, true);
		} else {
			Logger.info("toggling mobile data connection");
			try {
				int defaultAPN = getDefaultAPN(context);
				int fakeAPN = insertAPN(context, "Fake APN", "");
				setDefaultAPN(context, fakeAPN);
				try {
					Thread.sleep(MILLIS_TO_SLEEP);
				} catch (InterruptedException e) {
					// do nothing
				}
				setDefaultAPN(context, defaultAPN);
				removeAPN(context, fakeAPN);
			} catch (SecurityException se) {
				Logger
					.warn("could not access mobile settings - you will need to convert this app to a \"system app\" using Titanium Backup on a rooted phone");
			}
		}
	}
	
	private void setWifiEnabled(Context context, boolean enabled) {
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		boolean success = wifiManager.setWifiEnabled(enabled);
		if (!success) {
			Logger.warn("could not set \"wifi enabled\" to {0}", enabled);
		}
	}
	
	/**
	 * Insert a new APN entry into the system APN table.
	 * 
	 * @return the id that is automatically generated for the new apn entry
	 */
	private int insertAPN(Context context, String name, String apn_addr) {
		int id = -1;
		ContentResolver resolver = context.getContentResolver();
		ContentValues values = new ContentValues();
		values.put("name", name);
		values.put("apn", apn_addr);
		
		/*
		 * The following three field values are for testing in Android emulator only The APN setting page UI will ONLY
		 * display APNs whose 'numeric' filed is TelephonyProperties.PROPERTY_SIM_OPERATOR_NUMERIC. On Android emulator,
		 * this value is 310260, where 310 is mcc, and 260 mnc. With these field values, the newly added apn will appear
		 * in system UI.
		 */
		values.put("mcc", "310");
		values.put("mnc", "260");
		values.put("numeric", "310260");
		
		Cursor cursor = null;
		try {
			Uri newRow = resolver.insert(APN_TABLE_URI, values);
			if (newRow != null) {
				cursor = resolver.query(newRow, null, null, null, null);
				
				// Obtain the apn id
				int idindex = cursor.getColumnIndex("_id");
				cursor.moveToFirst();
				id = cursor.getShort(idindex);
			}
		} catch (SQLException e) {
			Logger.debug(e.toString());
		}
		
		if (cursor != null) {
			cursor.close();
		}
		return id;
	}
	
	/**
	 * Remove an APN entry into the system APN table.
	 */
	private boolean removeAPN(Context context, int id) {
		ContentResolver resolver = context.getContentResolver();
		
		int deleted = 0;
		try {
			deleted = resolver.delete(APN_TABLE_URI, "_id=" + id, null);
		} catch (SQLException e) {
			Logger.debug(e.toString());
		}
		
		return deleted == 1;
	}
	
	/**
	 * Set an apn to be the default apn for web traffic.
	 */
	private boolean setDefaultAPN(Context context, int id) {
		boolean success = false;
		ContentResolver resolver = context.getContentResolver();
		ContentValues values = new ContentValues();
		
		// See /etc/apns-conf.xml. The TelephonyProvider uses this file to provide
		// content://telephony/carriers/preferapn URI mapping
		values.put("apn_id", id);
		try {
			resolver.update(PREFERRED_APN_URI, values, null, null);
			Cursor cursor = resolver.query(PREFERRED_APN_URI, new String[] {"name", "apn"}, "_id=" + id, null, null);
			if (cursor != null) {
				success = true;
				cursor.close();
			}
		} catch (SQLException e) {
			Logger.debug(e.toString());
		}
		return success;
	}
	
	/**
	 * Get the default APN id.
	 */
	private int getDefaultAPN(Context context) {
		int ret = -1;
		ContentResolver resolver = context.getContentResolver();
		Cursor cursor = resolver.query(PREFERRED_APN_URI, new String[] {"_id"}, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			ret = Integer.parseInt(cursor.getString(0));
			cursor.close();
		}
		return ret;
	}
}
