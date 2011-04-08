package mobisocial.nfc.ndefexchange;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

import mobisocial.nfc.NfcInterface;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

/**
 * <p>Implements an Ndef push handover request in which a static tag
 * represents an Ndef reader device listening on a TCP socket.
 * </p>
 * <p>
 * Your application must hold the {@code android.permission.INTERNET}
 * permission to support TCP handovers.
 * </p>
 */
public class NdefTcpPushHandover implements ConnectionHandover {
	private static final int DEFAULT_TCP_HANDOVER_PORT = 7924;
	
	@Override
	public boolean supportsRequest(NdefRecord handoverRequest) {
		if (handoverRequest.getTnf() != NdefRecord.TNF_ABSOLUTE_URI
				|| !Arrays.equals(handoverRequest.getType(), NdefRecord.RTD_URI)) {
			return false;
		}
		
		String uriString = new String(handoverRequest.getPayload());
		if (uriString.startsWith("ndef+tcp://")) {
			return true;
		}
		
		return false;
	}

	@Override
	public void doConnectionHandover(NdefMessage handoverMessage, int record, NfcInterface nfcInterface) throws IOException {
		NdefRecord handoverRequest = handoverMessage.getRecords()[record];
		NdefMessage outboundNdef = nfcInterface.getForegroundNdefMessage();
		if (outboundNdef == null) return;
		
		String uriString = new String(handoverRequest.getPayload());
		URI uri = URI.create(uriString);
		sendNdefOverTcp(uri, nfcInterface);
	}

	private void sendNdefOverTcp(URI target, NfcInterface ndefProxy) throws IOException {
		String host = target.getHost();
		int port = target.getPort();
		if (port == -1) {
			port = DEFAULT_TCP_HANDOVER_PORT;
		}

		DuplexSocket socket = new TcpDuplexSocket(host, port);
		new NdefExchangeThread(socket, ndefProxy).start();
	}
}