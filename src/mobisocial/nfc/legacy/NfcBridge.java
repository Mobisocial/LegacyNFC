package mobisocial.nfc.legacy;

interface NfcBridge {
	public void stop();
	public void start();
	public String getReference();
}