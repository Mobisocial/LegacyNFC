package mobisocial.nfc.ndefexchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public class BluetoothDuplexSocket implements DuplexSocket {
	final String mmMac;
	final UUID mmServiceUuid;
	final BluetoothAdapter mmBluetoothAdapter;
	BluetoothSocket mmSocket;

	public BluetoothDuplexSocket(BluetoothAdapter adapter, String mac, UUID serviceUuid) throws IOException {
		mmBluetoothAdapter = adapter;
		mmMac = mac;
		mmServiceUuid = serviceUuid;
	}
	
	@Override
	public void connect() throws IOException {
		BluetoothDevice device = mmBluetoothAdapter.getRemoteDevice(mmMac);
		mmSocket = device.createInsecureRfcommSocketToServiceRecord(mmServiceUuid);
		mmSocket.connect();
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