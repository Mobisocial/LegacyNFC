package mobisocial.nfc.ndefexchange;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.util.Log;

import mobisocial.nfc.NdefHandler;
import mobisocial.nfc.NfcInterface;
import mobisocial.nfc.PrioritizedHandler;

public class ConnectionHandoverManager implements NdefHandler, PrioritizedHandler {
	public static final String USER_HANDOVER_PREFIX = "ndef://wkt:hr/";
	public static final String TAG = "connectionhandover";
	public static final int HANDOVER_PRIORITY = 5;
	private final Set<ConnectionHandover> mmConnectionHandovers = new LinkedHashSet<ConnectionHandover>();
	private final NfcInterface mNfc;
	private final NdefHandler mNdefHandler; // TODO hack
	
	public ConnectionHandoverManager(NfcInterface nfc) {
		mNfc = nfc;
		mNdefHandler = new NdefHandler() {
			@Override
			public int handleNdef(NdefMessage[] ndefMessages) {
				mNfc.handleNdef(ndefMessages);
				return NDEF_CONSUME;
			}
		};

		mmConnectionHandovers.add(new NdefBluetoothPushHandover());
		mmConnectionHandovers.add(new NdefTcpPushHandover());
	}
	
	public void addConnectionHandover(ConnectionHandover handover) {
		mmConnectionHandovers.add(handover);
	}
	
	public void clearConnectionHandovers() {
		mmConnectionHandovers.clear();
	}
	
	public boolean removeConnectionHandover(ConnectionHandover handover) {
		return mmConnectionHandovers.remove(handover);
	}

	/**
	 * Returns the (mutable) set of active connection handover
	 * responders.
	 */
	public Set<ConnectionHandover> getConnectionHandoverResponders() {
		return mmConnectionHandovers;
	}
	
	@Override
	public final int handleNdef(NdefMessage[] handoverRequest) {
		// TODO: What does this mean?
		return doHandover(handoverRequest[0], mNfc.getForegroundNdefMessage());
	}

	public final int doHandover(NdefMessage handoverRequest, final NdefMessage outboundNdef) {
		if (!isHandoverRequest(handoverRequest)) {
			return NDEF_PROPAGATE;
		}
		Log.d(TAG, "chm sending " + outboundNdef);
		NdefRecord[] records = handoverRequest.getRecords();
		for (int i = 2; i < records.length; i++) {
			Iterator<ConnectionHandover> handovers = mmConnectionHandovers.iterator();
			while (handovers.hasNext()) {
				ConnectionHandover handover = handovers.next();
				if (handover.supportsRequest(records[i])) {
					try {
						handover.doConnectionHandover(handoverRequest, i, outboundNdef, mNdefHandler);
						return NDEF_CONSUME;
					} catch (IOException e) {
						Log.w(TAG, "Handover failed.", e);
						// try the next one.
					}
				}
			}
		}

		return NDEF_PROPAGATE;
	}

	@Override
	public int getPriority() {
		return HANDOVER_PRIORITY;
	}

	/**
	 * Returns true if the given Ndef message contains a connection
	 * handover request.
	 */
	public static boolean isHandoverRequest(NdefMessage ndef) {
		NdefRecord[] records = (ndef).getRecords();

		// NFC Forum specification:
		if (records.length >= 3
			&& records[0].getTnf() == NdefRecord.TNF_WELL_KNOWN
			&& Arrays.equals(records[0].getType(), NdefRecord.RTD_HANDOVER_REQUEST)) {
			return true;
		}

		// User-space handover:
		// TODO: Support uri profile
		if (records.length > 0
				&& records[0].getTnf() == NdefRecord.TNF_ABSOLUTE_URI
				&& records[0].getPayload().length >= USER_HANDOVER_PREFIX.length()) {
			String scheme = new String(records[0].getPayload(), 0, USER_HANDOVER_PREFIX.length());
			return USER_HANDOVER_PREFIX.equals(scheme);
		}
		return false;
	}
}