package org.zephyrsoft.checknetwork;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.zephyrsoft.checknetwork.util.Logger;

/**
 * Manages the periodic checking. Also gets notified after reboots.
 * 
 * @author Mathis Dirksen-Thedens
 */
public class PeriodicCheckingManager extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.debug("triggered by system");
		enableOrDisablePeriodicChecking(context);
	}
	
	public static void enableOrDisablePeriodicChecking(Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		boolean checkingEnabled = preferences.getBoolean(context.getString(R.string.keyEnableNetworkChecking), false);
		if (checkingEnabled) {
			int intervalInMinutes =
				Integer.parseInt(preferences.getString(context.getString(R.string.keyCheckInterval), "5"));
			schedulePeriodicIntents(context, intervalInMinutes);
		} else {
			unschedulePeriodicIntents(context);
		}
	}
	
	private static void schedulePeriodicIntents(Context context, int intervalInMinutes) {
		Logger.debug("scheduling periodic intents every {0} minutes", intervalInMinutes);
		AlarmManager alarmService = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmService.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
			intervalInMinutes * 60 * 1000, createPendingIntent(context));
	}
	
	private static void unschedulePeriodicIntents(Context context) {
		Logger.debug("unscheduling periodic intents");
		AlarmManager alarmService = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmService.cancel(createPendingIntent(context));
	}
	
	private static PendingIntent createPendingIntent(Context context) {
		Intent intentToSchedule = new Intent(context, ConnectivityChecker.class);
		PendingIntent pendingIntent =
			PendingIntent.getBroadcast(context, 0, intentToSchedule, PendingIntent.FLAG_CANCEL_CURRENT);
		return pendingIntent;
	}
	
}
