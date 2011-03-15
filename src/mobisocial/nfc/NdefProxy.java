package mobisocial.nfc;

import android.nfc.NdefMessage;

public interface NdefProxy {
	public void handleNdef(NdefMessage ndef);
	public NdefMessage getForegroundNdefMessage();
}
