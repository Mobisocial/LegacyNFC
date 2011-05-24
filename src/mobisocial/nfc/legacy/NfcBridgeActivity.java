package mobisocial.nfc.legacy;


import org.apache.commons.codec.binary.Base64;

import mobisocial.ndefexchange.PendingNdefExchange;
import mobisocial.nfc.ConnectionHandoverManager;
import mobisocial.nfc.R;
import mobisocial.nfc.util.NdefHelper;
import mobisocial.nfc.util.QR;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class NfcBridgeActivity extends Activity {
	protected static final String TAG = "nfcserver";
	protected static final String ACTION_UPDATE = "mobisocial.intent.UPDATE";
	private static final String PREFERENCE_AUTOLAUNCH = "autolaunch";
	protected static final int QR_NFC_PAIR = 345;
	private TextView mStatusView = null;
	private Button mToggleButton = null;
	private Button mConfigButton = null;
	private Button mPairButton = null;
	private Button mFriendsButton = null;
	private CheckBox mAutoOpenCheckBox = null;
	private static PendingNdefExchange mNdefExchange;
	private SharedPreferences mPreferences;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mPreferences = getSharedPreferences("main", 0);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_UPDATE);
        
        mToggleButton = (Button)findViewById(R.id.toggle);
        mFriendsButton = (Button)findViewById(R.id.friends);
        mConfigButton = (Button)findViewById(R.id.config);
        mPairButton = (Button)findViewById(R.id.pair);
        mStatusView = (TextView)findViewById(R.id.status);
        mAutoOpenCheckBox = (CheckBox)findViewById(R.id.autolaunch);

        mPairButton.setEnabled(false);
        mConfigButton.setEnabled(false);

        mFriendsButton.setOnClickListener(mFriendsLauncher);
        //mFriendsButton.setVisibility(View.GONE);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
	    doBindService();
	    registerReceiver(mUpdateReceiver, mIntentFilter);
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	unregisterReceiver(mUpdateReceiver);
    	doUnbindService();
    }
    
    private View.OnClickListener mToggleBridge = new View.OnClickListener() {
    	public void onClick(View v) {
    		if (mBoundService.isBridgeRunning()) {
    			mBoundService.disableBridge();
    		} else {
    			mBoundService.enableBridge();
    		}
    	}
    };

    private View.OnClickListener mFriendsLauncher = new View.OnClickListener() {
        public void onClick(View v) {
            startActivity(new Intent(NfcBridgeActivity.this, FriendsActivity.class));
        }
    };

    private View.OnClickListener mConfigListener= new View.OnClickListener() {
    	public void onClick(View v) {
    		if (!mBoundService.isBridgeRunning()) {
        		Toast.makeText(NfcBridgeActivity.this, "Service must be running.", Toast.LENGTH_SHORT).show();
        	} else {
        		String handover = mBoundService.getBridgeReference();
                String content = ConnectionHandoverManager.USER_HANDOVER_PREFIX + new String(Base64.encodeBase64(
        				NdefHelper.getHandoverNdef(handover).toByteArray()));
        		String qr = QR.getQrl(content);
        		Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(qr));
        		startActivity(view);
        	}
    	}
    };

    private View.OnClickListener mPairListener= new View.OnClickListener() {
    	public void onClick(View v) {
    		if (!mBoundService.isBridgeRunning()) {
        		Toast.makeText(NfcBridgeActivity.this, "Service must be running.", Toast.LENGTH_SHORT).show();
        	} else {
        		Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
                startActivityForResult(intent, QR_NFC_PAIR);
        	}
    	}
    };
    
    private OnCheckedChangeListener mAutoLaunchListener = new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Editor editor = mPreferences.edit();
        editor.putBoolean(PREFERENCE_AUTOLAUNCH, isChecked);
        editor.commit();
        mBoundService.setAutoLaunch(isChecked);
      }
    };
    
    private void buildUi() {
    	mToggleButton.setOnClickListener(mToggleBridge);
        mConfigButton.setOnClickListener(mConfigListener);
        mPairButton.setOnClickListener(mPairListener);
        mAutoOpenCheckBox.setChecked(mPreferences.getBoolean(PREFERENCE_AUTOLAUNCH, false));
        mAutoOpenCheckBox.setOnCheckedChangeListener(mAutoLaunchListener);

    	if (!mBoundService.isBridgeRunning()) {
    		mStatusView.setText(R.string.bridge_not_running);
    		mToggleButton.setText(R.string.enable_bridge);
    		mPairButton.setEnabled(false);
            mConfigButton.setEnabled(false);
    	} else {
    		mStatusView.setText("Bridge running on " + mBoundService.getBridgeReference());
    		mPairButton.setEnabled(true);
            mConfigButton.setEnabled(true);
    		/*if (mBoundService.isPaired()) {
    			mPairStatusView.setText("Quick-tap enabled.");
    		} else {
    			mPairStatusView.setText("No quick-tap device set.");
    		}*/
    		mToggleButton.setText(R.string.disable_bridge);
    	}
    }

    
    /* Service binding */
    BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context arg0, Intent arg1) {
			NfcBridgeActivity.this.buildUi();
		}
	};
	
	IntentFilter mIntentFilter;

    private boolean mIsBound;
    private NfcBridgeService mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBoundService = ((NfcBridgeService.LocalBinder)service).getService();
            mBoundService.setAutoLaunch(mPreferences.getBoolean(PREFERENCE_AUTOLAUNCH, false));
            buildUi();
        }

        public void onServiceDisconnected(ComponentName className) {
            mBoundService = null;
        }
    };

    void doBindService() {
        bindService(new Intent(NfcBridgeActivity.this, 
                NfcBridgeService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == QR_NFC_PAIR) {
        	try {
	            if (resultCode != RESULT_OK) {
	            	throw new Exception();
	            }
                String data = intent.getStringExtra("SCAN_RESULT");
                if (!data.startsWith(ConnectionHandoverManager.USER_HANDOVER_PREFIX)) {
                	throw new Exception();
                }
                NdefMessage ndef = new NdefMessage(android.util.Base64.decode(
                		data.substring(ConnectionHandoverManager.USER_HANDOVER_PREFIX.length()),
                		android.util.Base64.URL_SAFE));
               mNdefExchange = new PendingNdefExchange(ndef, null);
        	} catch (Exception e) {
        		toast("Could not set nfc partner.");
        	}
        }
    }
    
    public static PendingNdefExchange getNdefExchange() {
    	return mNdefExchange;
    }

    public void toast(String text) {
    	Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}