package mobisocial.nfc.legacy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.nfc.NdefMessage;
import android.util.Log;

import mobisocial.ndefexchange.DuplexSocket;
import mobisocial.ndefexchange.NdefExchangeContract;

/**
 * Runs a thread during a connection handover with a remote device over a
 * {@see DuplexSocket}, transmitting the given Ndef message.
 */
class HandoverConnectedThread extends Thread {
    private static final String TAG = "NdefHandover";
	public static final byte HANDOVER_VERSION = 0x19;
	private final DuplexSocket mmSocket;
	private final InputStream mmInStream;
	private final OutputStream mmOutStream;
	private final NdefExchangeContract mmNdefProxy;
	
	private boolean mmIsWriteDone = false;
	private boolean mmIsReadDone = false;
	
	public HandoverConnectedThread(DuplexSocket socket, NdefExchangeContract ndefProxy) {
		mmNdefProxy = ndefProxy;
		mmSocket = socket;
		InputStream tmpIn = null;
		OutputStream tmpOut = null;

		try {
			socket.connect();
			tmpIn = socket.getInputStream();
			tmpOut = socket.getOutputStream();
		} catch (IOException e) {
			Log.e(TAG, "temp sockets not created", e);
		}

		mmInStream = tmpIn;
		mmOutStream = tmpOut;
	}

	public void run() {
		try {
			if (mmInStream == null || mmOutStream == null) {
				return;
			}

			// Read on this thread, write on a new one.
			new SendNdefThread().start();

			DataInputStream dataIn = new DataInputStream(mmInStream);
			byte version = (byte) dataIn.readByte();
			if (version != HANDOVER_VERSION) {
				throw new Exception("Bad handover protocol version.");
			}
			int length = dataIn.readInt();
			if (length > 0) {
				byte[] ndefBytes = new byte[length];
				int read = 0;
				while (read < length) {
					read += dataIn.read(ndefBytes, read, (length - read));
				}
				NdefMessage ndef = new NdefMessage(ndefBytes);
				mmNdefProxy.handleNdef(new NdefMessage[] {ndef});
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to issue handover.", e);
		} finally {
			synchronized(HandoverConnectedThread.this) {
				mmIsReadDone = true;
				if (mmIsWriteDone) {
					cancel();
				}
			}
		}
	}

	public void cancel() {
		try {
			mmSocket.close();
		} catch (IOException e) {}
	}
	
	
	private class SendNdefThread extends Thread {
		@Override
		public void run() {
			try {
				NdefMessage outbound = mmNdefProxy.getForegroundNdefMessage();
				DataOutputStream dataOut = new DataOutputStream(mmOutStream);
				dataOut.writeByte(HANDOVER_VERSION);
				if (outbound != null) {
					byte[] ndefBytes = outbound.toByteArray();
					dataOut.writeInt(ndefBytes.length);
					dataOut.write(ndefBytes);
				} else {
					dataOut.writeInt(0);
				}
				dataOut.flush();
			} catch (IOException e) {
				Log.e(TAG, "Error writing to socket", e);
			} finally {
				synchronized(HandoverConnectedThread.this) {
					mmIsWriteDone = true;
					if (mmIsReadDone) {
						cancel();
					}
				}
			}
		}
	}
}