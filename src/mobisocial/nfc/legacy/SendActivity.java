package mobisocial.nfc.legacy;

import mobisocial.nfc.R;
import mobisocial.nfc.ndefexchange.ConnectionHandoverManager;
import mobisocial.nfc.ndefexchange.PendingNdefExchange;
import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

public class SendActivity extends Activity {
	private int QR_NFC_PAIR = 98743;
	private PendingNdefExchange mPartner;
	private NdefMessage mNdef;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_send);

		// TODO: This should be in data.
		// NdefFactory.ndefToUri(), uriToNdef().
		mNdef = getIntent().getParcelableExtra("ndef");
		if (mPartner == null && NfcBridgeActivity.getNdefExchange() != null) {
			mPartner = NfcBridgeActivity.getNdefExchange();
		} else {
			findViewById(R.id.quicktap).setEnabled(false);
		}

		findViewById(R.id.quicktap).setOnClickListener(doQuicktap);
		findViewById(R.id.scanqr).setOnClickListener(doScanQR);
		findViewById(R.id.junction).setOnClickListener(doJunction);
	}
	
	private View.OnClickListener doQuicktap = new View.OnClickListener() {
		@Override
		public void onClick(View arg0) {
			if (mPartner != null) {
				toast("Sending ndef.");
				mPartner.exchangeNdef(mNdef);
				finish();
			} else {
				toast("No quicktap target set.");
			}
		}
	};
	
	private View.OnClickListener doScanQR = new View.OnClickListener() {
		@Override
		public void onClick(View arg0) {
			toast("Scan to initiate ndef exchange.");
			Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
            startActivityForResult(intent, QR_NFC_PAIR);
		}
	};
	
	private View.OnClickListener doJunction = new View.OnClickListener() {
		@Override
		public void onClick(View arg0) {
			// TODO:
			// newJunction().sendMessage(content);
		}
	};
	
	private void tryToSend() {
		if (mNdef != null) {
			if (mPartner != null) {
				toast("Exchanging ndef " + new String(mNdef.getRecords()[0].getPayload()));
				mPartner.exchangeNdef(mNdef);
				finish();
			}
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
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
                mPartner = new PendingNdefExchange(ndef, NfcBridgeService.getInstance());
                tryToSend();
        	} catch (Exception e) {
        		toast("Could not set nfc partner.");
        	}
        }
	}
	
	private void toast(String text) {
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
	}
}
