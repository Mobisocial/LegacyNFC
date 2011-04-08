package mobisocial.nfc;

import android.nfc.NdefMessage;

public interface NfcInterface {
	public void handleNdef(NdefMessage[] ndef);
	public NdefMessage getForegroundNdefMessage();
	public void setForegroundNdefMessage(NdefMessage ndef);
	public void share(Object shared);
}