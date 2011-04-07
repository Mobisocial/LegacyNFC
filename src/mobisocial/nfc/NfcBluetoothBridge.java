package mobisocial.nfc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.UUID;

import mobisocial.bluetooth.InsecureBluetooth;
import mobisocial.nfc.NfcBridgeService.DuplexSocket;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.nfc.NdefMessage;
import android.util.Log;

public class NfcBluetoothBridge implements NfcBridge {
	private static final String TAG = "nfcserver";
	private final UUID mServiceUuid;
	private AcceptThread mAcceptThread;
	private final NdefProxy mNdefProxy;
	private final BluetoothAdapter mBtAdapter;

	public NfcBluetoothBridge(NdefProxy ndefProxy, UUID serviceUuid) {
		mNdefProxy = ndefProxy;
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		mServiceUuid = serviceUuid;
	}

	public String getReference() {
		return "ndef+bluetooth://" + mBtAdapter.getAddress() + "/" + mServiceUuid;
	}

	/**
	 * Starts the listening service
	 */
	public void start() {
		if (mAcceptThread != null)
			return;

		mAcceptThread = new AcceptThread();
		mAcceptThread.start();
	}

	public void stop() {
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
	}

	private class AcceptThread extends Thread {
		// The local server socket
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread() {
			BluetoothServerSocket tmp = null;

			// Create a new listening server socket
			try {
				tmp = InsecureBluetooth.listenUsingRfcommWithServiceRecord(mBtAdapter, "NfcHandover", mServiceUuid, false);
			} catch (IOException e) {
				System.err.println("Could not open server socket");
				e.printStackTrace(System.err);
			}
			mmServerSocket = tmp;
		}

		public void run() {
			setName("AcceptThread");
			DuplexSocket duplexSocket = null;
			
			// Listen to the server socket always
			while (true) {
				try {
					// This is a blocking call and will only return on a
					// successful connection or an exception
					Log.d(TAG, "waiting for client...");
					BluetoothSocket socket = mmServerSocket.accept();
					Log.d(TAG, "Client connected!");
					duplexSocket = new BluetoothDuplexSocket(socket);
				} catch (SocketException e) {

				} catch (IOException e) {
					Log.e(TAG, "accept() failed", e);
					break;
				}

				// If a connection was accepted
				if (duplexSocket == null) {
					break;
				}

				
				new HandoverConnectedThread(duplexSocket, mNdefProxy).start();
			}
			Log.d(TAG, "END mAcceptThread");
		}

		public void cancel() {
			Log.d(TAG, "cancel " + this);
			try {
				mmServerSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of server failed", e);
			}
		}
	}

	static class BluetoothDuplexSocket implements DuplexSocket {
		final BluetoothSocket mmSocket;

		public BluetoothDuplexSocket(BluetoothSocket socket) throws IOException {
			mmSocket = socket;
		}
		
		@Override
		public void connect() throws IOException {
			// already connected
		}
		
		@Override
		public InputStream getInputStream() throws IOException {
			return mmSocket.getInputStream();
		}
		
		@Override
		public OutputStream getOutputStream() throws IOException {
			return mmSocket.getOutputStream();
		}
		
		@Override
		public void close() throws IOException {
			if (mmSocket != null) {
				mmSocket.close();
			}
		}
	}

	/**
	 * Runs a thread during a connection handover with a remote device over a
	 * {@see DuplexSocket}, transmitting the given Ndef message.
	 */
	private static class HandoverConnectedThread extends Thread {
		public static final byte HANDOVER_VERSION = 0x19;
		private final DuplexSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
		private final NdefProxy mmNdefProxy;
		
		private boolean mmIsWriteDone = false;
		private boolean mmIsReadDone = false;
		
		public HandoverConnectedThread(DuplexSocket socket, NdefProxy ndefProxy) {
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
					mmNdefProxy.handleNdef(ndef);
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
}