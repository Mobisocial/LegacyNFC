package mobisocial.nfc.ndefexchange;

import java.io.IOException;

import mobisocial.nfc.NfcInterface;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

/**
 * An interface for classes supporting communication established using
 * Nfc but with an out-of-band data transfer.
 */
public interface ConnectionHandover {
	/**
	 * Issues a connection handover of the given type.
	 * @param handoverRequest The connection handover request message.
	 * @param recordNumber The index of the handover record entry being attempted.
	 * @param nfcInterface The Nfc interface for sending and receiving ndef messages.
	 * @throws IOException
	 */
	public void doConnectionHandover(NdefMessage handoverRequest, int recordNumber, NfcInterface nfcInterface) throws IOException;
	public boolean supportsRequest(NdefRecord record);
}
