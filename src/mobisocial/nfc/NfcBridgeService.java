package mobisocial.nfc;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import com.android.apps.tag.record.UriRecord;

import edu.stanford.mobisocial.appmanifest.ApplicationManifest;
import edu.stanford.mobisocial.appmanifest.platforms.PlatformReference;
import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;

public class NfcBridgeService extends Service implements NdefProxy {
	private static final String TAG = NfcBridgeActivity.TAG;
	private static NfcBridge mNfcBridge = null;
	private NotificationManager mNotificationManager;

	public static final String ACTION_HANDLE_NDEF = "mobisocial.intent.action.HANDLE_NDEF";
	public static final String ACTION_SET_NDEF = "mobisocial.intent.action.SET_NDEF";
	public static final String EXTRA_APPLICATION_ARGUMENT = "android.intent.extra.APPLICATION_ARGUMENT";
	public static final String MESSAGE_RECEIVED = "Nfc message received.";

	private Intent mNotifyIntent;
	private UUID mServiceUuid;
	private NdefMessage mForegroundMessage;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mNotificationManager = (NotificationManager)getSystemService(Service.NOTIFICATION_SERVICE);
		mNotifyIntent = new Intent(NfcBridgeActivity.ACTION_UPDATE);
		mNotifyIntent.setPackage(getPackageName());

		mScreenChangedFilter = new IntentFilter();
		mScreenChangedFilter.addAction(Intent.ACTION_SCREEN_ON);
		mScreenChangedFilter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(mScreenChangedReceiver, mScreenChangedFilter);
		
		mSetNdefFilter = new IntentFilter();
		mSetNdefFilter.addAction(ACTION_SET_NDEF);
		registerReceiver(mSetNdefReceiver, mSetNdefFilter);
		
		SharedPreferences preferences = getSharedPreferences("main", 0);
		String uuid = preferences.getString("serviceUuid", null);
		if (uuid == null) {
			uuid = UUID.randomUUID().toString();
			preferences.edit().putString("serviceUuid", uuid).commit();
		}
		mServiceUuid = UUID.fromString(uuid);
	}
	
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mScreenChangedReceiver);
	}
	
	public void startSelf() {
		startService(new Intent(this, NfcBridgeService.class));
	}
	
	public class LocalBinder extends Binder {
        NfcBridgeService getService() {
            return NfcBridgeService.this;
        }
    }
	
	public synchronized void enableBridge() {
		if (mNfcBridge != null) {
			Log.w(TAG, "Nfc bridge already running.");
			return;
		}
			
		mNfcBridge = new NfcBluetoothBridge(this, mServiceUuid);
		mNfcBridge.start();
		sendBroadcast(mNotifyIntent);
		startSelf();
	}
	
	public synchronized void disableBridge() {
		if (mNfcBridge == null) {
			Log.w(TAG, "Nfc bridge not running.");
			return;
		}
		mNfcBridge.stop();
		mNfcBridge = null;
		sendBroadcast(mNotifyIntent);
		stopSelf();
	}
	
	public boolean isBridgeRunning() {
		// Always running if the screen is on and mNfcBridge is not null.
		return (mNfcBridge != null);
	}
	
	public String getBridgeReference() {
		return (mNfcBridge == null) ? null : mNfcBridge.getReference();
	}
	
	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	IntentFilter mScreenChangedFilter;
	BroadcastReceiver mScreenChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			synchronized(NfcBridgeService.this) {
				if (mNfcBridge == null) {
					return;
				}
				
				if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
					mNfcBridge.stop();
				} else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
					mNfcBridge.start();
				}
			}
		}
	};
	
	IntentFilter mSetNdefFilter;
	BroadcastReceiver mSetNdefReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
				Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
				mForegroundMessage = (NdefMessage)messages[0];
			} else {
				mForegroundMessage = null;
			}
		}
	};

	public void handleNdef(NdefMessage ndef) {
    	Intent handleNdefIntent = new Intent(ACTION_HANDLE_NDEF);
    	Parcelable[] messages = new Parcelable[] { ndef };
    	handleNdefIntent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, messages);
    	sendOrderedBroadcast(handleNdefIntent, Manifest.permission.NFC, mNdefReceiver, null, Activity.RESULT_OK, null, null);
	}
	
	private BroadcastReceiver mNdefReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent inboundIntent) {
			if (getResultCode() != Activity.RESULT_OK) {
				return;
			}

			Parcelable[] messages = inboundIntent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			NdefMessage ndef = (NdefMessage)messages[0];
			NdefRecord firstRecord = ndef.getRecords()[0];
	    	Notification notification = null;
	    	
			if (UriRecord.isUri(firstRecord)) {
	    		UriRecord uriRecord = UriRecord.parse(firstRecord);
	    		Intent intent = uriRecord.getIntentForUri();
	    		notification = new Notification(R.drawable.stat_sys_nfc, MESSAGE_RECEIVED, System.currentTimeMillis());
	    		PendingIntent contentIntent = PendingIntent.getActivity(NfcBridgeService.this, 0, intent, 0);
	    		notification.setLatestEventInfo(NfcBridgeService.this, MESSAGE_RECEIVED, "Click to visit webpage.", contentIntent);
	    	} else if (firstRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
	    		String webpage = null;
				String androidReference = null;

				byte[] manifestBytes = ndef.getRecords()[0].getPayload();
				ApplicationManifest manifest = new ApplicationManifest(
						manifestBytes);
				List<PlatformReference> platforms = manifest
						.getPlatformReferences();
				for (PlatformReference platform : platforms) {
					int platformId = platform.getPlatformIdentifier();
					switch (platformId) {
					case ApplicationManifest.PLATFORM_WEB_GET:
						webpage = new String(platform.getAppReference());
						break;
					case ApplicationManifest.PLATFORM_ANDROID_PACKAGE:
						androidReference = new String(platform.getAppReference());
						break;
					}
				}
				
				if (androidReference != null) {
					int col = androidReference.indexOf(":");
		    		String pkg = androidReference.substring(0, col);
		    		String arg = androidReference.substring(col+1);
		    		
		    		Intent intent = new Intent(Intent.ACTION_MAIN);
		    		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		    		intent.setPackage(pkg);
		    		intent.putExtra(EXTRA_APPLICATION_ARGUMENT, arg);
		    		
		    		// TODO: support applications that aren't yet installed.
		    		List<ResolveInfo> resolved = getPackageManager().queryIntentActivities(intent, 0);
		    		if (resolved.size() > 0) {
		    			ActivityInfo info = resolved.get(0).activityInfo;
		    			intent.setComponent(new ComponentName(info.packageName, info.name));
		    			
		    			notification = new Notification(R.drawable.stat_sys_nfc, MESSAGE_RECEIVED, System.currentTimeMillis());
		    			PendingIntent contentIntent = PendingIntent.getActivity(NfcBridgeService.this, 0, intent, 0);
			    		notification.setLatestEventInfo(NfcBridgeService.this, "Nfc message received.", "Click to launch application.", contentIntent);
		    		}
				} else if (webpage != null) {
					notification = new Notification(R.drawable.stat_sys_nfc, MESSAGE_RECEIVED, System.currentTimeMillis());
		    		Intent intent = new Intent(Intent.ACTION_VIEW);
		    		intent.setData(Uri.parse(webpage));
		    		PendingIntent contentIntent = PendingIntent.getActivity(NfcBridgeService.this, 0, intent, 0);
		    		notification.setLatestEventInfo(NfcBridgeService.this, "Nfc message received.", "Click to visit webpage.", contentIntent);
				}
			}
	    	
	    	if (notification != null) {
	    		notification.flags = Notification.FLAG_AUTO_CANCEL;
	    		mNotificationManager.notify(0, notification);
	    	}
		}
	};

	@Override
	public NdefMessage getForegroundNdefMessage() {
		return mForegroundMessage;
	}

	/**
	 * A wrapper for the standard Socket implementation.
	 *
	 */
	public interface DuplexSocket extends Closeable {
		public InputStream getInputStream() throws IOException;
		public OutputStream getOutputStream() throws IOException;
		public void connect() throws IOException;
	}
}