package mobisocial.nfc.util;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

public class NdefHelper {
	public static NdefMessage getHandoverNdef(String ref) {
		NdefRecord[] records = new NdefRecord[3];
		
		/* Handover Request */
		byte[] version = new byte[] { (0x1 << 4) | (0x2) };
		records[0] = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_HANDOVER_REQUEST, new byte[0], version);

		/* Collision Resolution */
		records[1] = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, new byte[] {
				0x63, 0x72 }, new byte[0], new byte[] {0x0, 0x0});

		/* Handover record */
		records[2] = new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI,
				NdefRecord.RTD_URI, new byte[0], ref.getBytes());
		
		return new NdefMessage(records);
	}
}
