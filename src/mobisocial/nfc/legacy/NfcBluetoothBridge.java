package mobisocial.nfc.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.UUID;

import mobisocial.bluetooth.InsecureBluetooth;
import mobisocial.ndefexchange.DuplexSocket;
import mobisocial.ndefexchange.NdefExchangeContract;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.nfc.NdefMessage;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;

public class NfcBluetoothBridge implements NfcBridge {
	private static final String TAG = "nfcserver";
	private final UUID mServiceUuid;
	private AcceptThread mAcceptThread;
	private final NdefExchangeContract mNdefProxy;
	private final BluetoothAdapter mBtAdapter;

	public NfcBluetoothBridge(NdefExchangeContract ndefProxy, UUID serviceUuid) {
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
				if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD_MR1) {
					tmp = mBtAdapter.listenUsingInsecureRfcommWithServiceRecord("NfcHandover", mServiceUuid);
				} else {
					tmp = InsecureBluetooth.listenUsingRfcommWithServiceRecord(mBtAdapter, "NfcHandover", mServiceUuid, false);
				}
			} catch (IOException e) {
				Log.e(TAG, "Could not open server socket", e);
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
}