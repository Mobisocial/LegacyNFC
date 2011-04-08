package mobisocial.nfc.ndefexchange;

import mobisocial.nfc.NfcInterface;
import android.nfc.NdefMessage;

/**
 * An Ndef Exchange connection handover that is ready to be executed.
 *
 */
public class PendingNdefExchange {
	private final NdefMessage mHandover;
	private final NfcInterface mNfcInterface;
	private final ConnectionHandoverManager mConnectionHandoverManager;

	public PendingNdefExchange(NdefMessage handover, NfcInterface nfcInterface) {
		mHandover = handover;
		mNfcInterface = nfcInterface;
		mConnectionHandoverManager = new ConnectionHandoverManager(nfcInterface);
	}
	
	public void exchangeNdef(NdefMessage ndef) {
		mConnectionHandoverManager.doHandover(mHandover, ndef);
	}
}
