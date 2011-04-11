package mobisocial.nfc.ndefexchange;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import mobisocial.nfc.NdefHandler;
import mobisocial.nfc.NfcInterface;

import android.bluetooth.BluetoothAdapter;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

/**
 * <p>Implements an Ndef push handover request in which a static tag
 * represents an Ndef reader device listening on a Bluetooth socket.
 * </p>
 * <p>
 * Your application must hold the {@code android.permission.BLUETOOTH}
 * permission to support Bluetooth handovers.
 * </p>
 */
public class NdefBluetoothPushHandover implements ConnectionHandover {
	final BluetoothAdapter mmBluetoothAdapter;
	
	public NdefBluetoothPushHandover() {
		mmBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public boolean supportsRequest(NdefRecord handoverRequest) {
		if (handoverRequest.getTnf() != NdefRecord.TNF_ABSOLUTE_URI
				|| !Arrays.equals(handoverRequest.getType(), NdefRecord.RTD_URI)) {
			return false;
		}

		String uriString = new String(handoverRequest.getPayload());
		if (uriString.startsWith("ndef+bluetooth://")) {
			return true;
		}
		
		return false;
	}

	@Override
	public void doConnectionHandover(NdefMessage handoverMessage, int record, NdefMessage outbound, NdefHandler inboundHandler) throws IOException {
		NdefRecord handoverRequest = handoverMessage.getRecords()[record];
		String uriString = new String(handoverRequest.getPayload());
		Uri target = Uri.parse(uriString);
		
		String mac = target.getAuthority();
		UUID uuid = UUID.fromString(target.getPath().substring(1));
		DuplexSocket socket = new BluetoothDuplexSocket(mmBluetoothAdapter, mac, uuid);
		new NdefExchangeThread(socket, outbound, inboundHandler).start();
	}
}