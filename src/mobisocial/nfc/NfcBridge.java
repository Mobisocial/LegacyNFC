package mobisocial.nfc;

interface NfcBridge {
	public void stop();
	public void start();
	public String getReference();
}