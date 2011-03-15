package mobisocial.nfc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import android.nfc.NdefMessage;
import android.util.Log;

public class NfcTcpBridge implements NfcBridge {
	private static final String TAG = "nfcserver";
	private static final int SERVER_PORT = 7924;
	private AcceptThread mAcceptThread;
	private final NdefProxy mNdefReceiver;

	public NfcTcpBridge(NdefProxy ndefReceiver) {
		mNdefReceiver = ndefReceiver;
	}

	public String getReference() {
		return "ndef+tcp://" + getLocalIpAddress() + ":" + SERVER_PORT;
	}

	/**
	 * Starts the simple file server
	 */
	public void start() {
		if (mAcceptThread != null)
			return;

		String ip = getLocalIpAddress();
		if (ip == null) {
			System.err.println("Error starting server");
			return;
		}

		System.out.println("Server running on " + ip + ":" + SERVER_PORT + ".");
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
		private final ServerSocket mmServerSocket;

		public AcceptThread() {
			ServerSocket tmp = null;

			// Create a new listening server socket
			try {
				tmp = new ServerSocket(SERVER_PORT);
			} catch (IOException e) {
				System.err.println("Could not open server socket");
				e.printStackTrace(System.err);
			}
			mmServerSocket = tmp;
		}

		public void run() {
			// Log.d(TAG, "BEGIN mAcceptThread" + this);
			setName("AcceptThread");
			Socket socket = null;

			// Listen to the server socket always
			while (true) {
				try {
					// This is a blocking call and will only return on a
					// successful connection or an exception
					Log.d(TAG, "waiting for client...");
					socket = mmServerSocket.accept();
					Log.d(TAG, "Client connected!");
				} catch (SocketException e) {

				} catch (IOException e) {
					Log.e(TAG, "accept() failed", e);
					break;
				}

				// If a connection was accepted
				if (socket == null) {
					break;
				}

				ConnectedThread conThread = new ConnectedThread(socket);
				conThread.start();
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

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread {
		private final Socket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
		private final int BUFFER_LENGTH = 2048;

		public ConnectedThread(Socket socket) {
			// Log.d(TAG, "create ConnectedThread");

			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			// Log.d(TAG, "BEGIN mConnectedThread");
			byte[] buffer = new byte[BUFFER_LENGTH];
			int bytes;

			if (mmInStream == null || mmOutStream == null)
				return;

			// Read header information, determine connection type
			try {
				bytes = mmInStream.read(buffer);
				byte[] ndefBytes = new byte[bytes];
				System.arraycopy(buffer, 0, ndefBytes, 0, bytes);
				NdefMessage ndefMessage = new NdefMessage(ndefBytes);
				mNdefReceiver.handleNdef(ndefMessage);
			} catch (Exception e) {
				Log.e(TAG, "Error reading connection header", e);
			}

			// No longer listening.
			cancel();
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

	private String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						// not ready for IPv6, apparently.
						if (!inetAddress.getHostAddress().contains(":")) {
							return inetAddress.getHostAddress().toString();
						}
					}
				}
			}
		} catch (SocketException ex) {

		}
		return null;
	}
}