package mobisocial.nfc.ndefexchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TcpDuplexSocket implements DuplexSocket {
		final Socket mSocket;

		public TcpDuplexSocket(String host, int port) throws IOException {
			mSocket = new Socket(host, port);
		}
		
		@Override
		public void connect() throws IOException {
			
		}
		
		@Override
		public InputStream getInputStream() throws IOException {
			return mSocket.getInputStream();
		}
		
		@Override
		public OutputStream getOutputStream() throws IOException {
			return mSocket.getOutputStream();
		}
		
		@Override
		public void close() throws IOException {
			mSocket.close();
		}
	}