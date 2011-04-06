package mobisocial.nfc;


import mobisocial.nfc.util.NdefHelper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class NfcBridgeActivity extends Activity {
	protected static final String TAG = "nfcserver";
	protected static final String ACTION_UPDATE = "mobisocial.intent.UPDATE";
	private TextView mStatusView = null;
	private Button mToggleButton = null;
	private Button mConfigButton = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_UPDATE);
        
        mToggleButton = (Button)findViewById(R.id.toggle);
        mConfigButton = (Button)findViewById(R.id.config);
        mStatusView = (TextView)findViewById(R.id.status);
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
    	mUiBuilt = false;
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

    private View.OnClickListener mConfigListener= new View.OnClickListener() {
    	public void onClick(View v) {
    		if (!mBoundService.isBridgeRunning()) {
        		Toast.makeText(NfcBridgeActivity.this, "Service must be running.", Toast.LENGTH_SHORT).show();
        	} else {
        		String handover = mBoundService.getBridgeReference();
        		String content = "ndefb://" + Base64.encodeToString(
        				NdefHelper.getHandoverNdef(handover).toByteArray(), Base64.URL_SAFE);
        		String qr = "http://chart.apis.google.com/chart?cht=qr&chs=350x350&chl=" + content;
        		Intent view = new Intent(Intent.ACTION_VIEW);
        		view.setData(Uri.parse(qr));
        	}
    	}
    };
    
    boolean mUiBuilt = false;
    private void buildUi() {
    	mToggleButton.setOnClickListener(mToggleBridge);
        mConfigButton.setOnClickListener(mConfigListener);

    	if (!mBoundService.isBridgeRunning()) {
    		mStatusView.setText(R.string.bridge_not_running);
    		mToggleButton.setText(R.string.enable_bridge);
    	} else {
    		mStatusView.setText("Bridge running on " + mBoundService.getBridgeReference());
    		mToggleButton.setText(R.string.disable_bridge);
    	}
    	mUiBuilt = true;
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
}