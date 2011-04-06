package mobisocial.nfc.util;

import java.net.URLEncoder;

public class QR {
	public static String getQrl(String url) {
		// @IgnoreException would let me do "UTF-8" encoding in one line.
		return "http://chart.apis.google.com/chart?cht=qr&chs=350x350&chl=" + URLEncoder.encode(url);
	}
}
