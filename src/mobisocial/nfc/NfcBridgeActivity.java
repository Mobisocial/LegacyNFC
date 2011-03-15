package mobisocial.nfc;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class NfcBridgeActivity extends Activity {
	protected static final String TAG = "nfcserver";
	protected static final String ACTION_UPDATE = "mobisocial.intent.UPDATE";
	private TextView mStatusView = null;
	private Button mToggleButton = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_UPDATE);
        
        mToggleButton = (Button)findViewById(R.id.toggle);
        mStatusView = (TextView)findViewById(R.id.status);
        
        mToggleButton.setOnClickListener(mToggleBridge);
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
    		if (!mUiBuilt) return;
    		if (mBoundService.isBridgeRunning()) {
    			mBoundService.disableBridge();
    		} else {
    			mBoundService.enableBridge();
    		}
    	}
    };
    
    boolean mUiBuilt = false;
    private void buildUi() {
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