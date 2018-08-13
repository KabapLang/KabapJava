/*
 * Copyright 2017-18 White Label Dev Ltd, and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kabap;

import static org.kabap.Kabap.logD;
import static org.kabap.Kabap.logV;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.kabap.Kabap.Extension;
import org.kabap.Kabap.ReferenceMessage;
import org.kabap.Kabap.ReferenceMessageResult;
import org.kabap.Kabap.ReferenceMessageType;

/**
 * This extension provides remote network access; it is considered DANGEROUS.
 * <p>
 * <b>WARNING:</b> This extension can be used to exfiltrate data from your system, requests for network data also
 * carry an outgoing data payload. If combined with the File extension it is possible to send the contents of the
 * local disk to a remote agent.
 * </p>
 *
 * @author WLD-PJ <wld-pj@kabap.org>
 * @version 1.0
 * @since 1.0
 */
public class KabapNet implements Extension {
	private static final String LOG_TAG = KabapNet.class.getSimpleName ();
	private static final String REFERENCE_PREFIX = "net";
	private static final int HTTP_TIMEOUT_CONNECT = 5000;
	private static final int HTTP_TIMEOUT_READ = 20000;

	private boolean DEBUG = false;
	private boolean requested = false;
	private String method = null;
	private String url = null;
	private HashMap<String, String> headers = null;
	private String postData = null;
	private int responseCode = -1;
	private String responseData = null;

	/**
	 * Constructor, reset the network request
	 */
	public KabapNet () {
		referenceReset ();
	}

	/**
	 * Register the extension reference
	 */
	public String referenceRegister (int version, boolean debug) {
		// Only turn debugging on not off
		DEBUG = DEBUG || debug;

		// This is a version 1 extension only
		if (version != 1) {
			if (DEBUG)
				logD (LOG_TAG, "Refusing to register this extension; Kabap v" + version + " is too high");

			return null;
		}

		if (DEBUG)
			logD (LOG_TAG, "Registering this extension with Kabap v" + version + " (debugging " + (debug ? "enabled" : "disabled") + ") with reference: " + REFERENCE_PREFIX);

		// Tell the Kabap instance this extension responds to any reference starting with this string
		return REFERENCE_PREFIX;
	}

	/**
	 * Reset the network request
	 */
	public void referenceReset () {
		if (DEBUG)
			logV (LOG_TAG, "Network request reset");

		requested = false;
		method = "GET";
		url = "";
		headers = new HashMap<String, String> ();
		postData = "";
		responseCode = -1;
		responseData = "";
	}

	/**
	 * Handle net operation messages, by default this extension skips unknown requests
	 */
	public ReferenceMessage referenceHandler (ReferenceMessage message) {
		if (DEBUG)
			logV (LOG_TAG, " IN referenceHandler(type=" + message.type + ", name=" + message.name + ", value=" + message.value + ")");

		// This extensions implements naming convention [reference].[key]
		String[] nameParts = message.name.toLowerCase ().split ("\\.");

		if (nameParts.length != 2) { // Ignoring this request as it is not the right naming convention
			message.result = Kabap.ReferenceMessageResult.IGNORED;
		} else if (nameParts[1].equals ("reset") && message.type == ReferenceMessageType.READ) {
			referenceReset ();
			message.value = "1";
			message.result = ReferenceMessageResult.HANDLED_OKAY;
		} else if (nameParts[1].equals ("method")) {
			if (message.type == ReferenceMessageType.READ) {
				message.value = method;
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			} else {
				String tmp = message.value.toUpperCase ();

				if (!tmp.equals ("POST") && !tmp.equals ("GET")) {
					message.value = "Method must be GET or POST";
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				} else {
					method = tmp;
					message.result = ReferenceMessageResult.HANDLED_OKAY;
				}
			}
		} else if (nameParts[1].equals ("url")) {
			if (message.type == ReferenceMessageType.READ) {
				message.value = url;
			} else {
				url = message.value.trim ();
			}

			message.result = ReferenceMessageResult.HANDLED_OKAY;
		} else if (nameParts[1].equals ("data")) {
			if (message.type == ReferenceMessageType.READ) {
				message.value = postData;
			} else {
				postData = message.value;
			}

			message.result = ReferenceMessageResult.HANDLED_OKAY;
		} else if (nameParts[1].equals ("header") && message.type == ReferenceMessageType.WRITE) {
			String[] tmp = message.value.trim ().split (":");
			if (tmp.length != 2) {
				message.value = "A header can contain only 1 colon";
				message.result = ReferenceMessageResult.HANDLED_FAIL;
			} else if (tmp[0].trim ().isEmpty ()) {
				message.value = "Header key cannot be empty";
				message.result = ReferenceMessageResult.HANDLED_FAIL;
			} else if (tmp[1].trim ().isEmpty ()) {
				message.value = "Header value cannot be empty";
				message.result = ReferenceMessageResult.HANDLED_FAIL;
			} else {
				headers.put (tmp[0].trim (), tmp[1].trim ());
				message.value = "1";
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			}
		} else if (nameParts[1].equals ("status") && message.type == ReferenceMessageType.READ) {
			if (checkNetRequest (message)) {
				message.value = String.valueOf (responseCode);
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			}
		} else if (nameParts[1].equals ("response") && message.type == ReferenceMessageType.READ) {
			if (checkNetRequest (message)) {
				message.value = responseData;
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			}
		} else if (nameParts[1].equals ("request") && message.type == ReferenceMessageType.READ) {
			if (url.isEmpty ()) {
				message.value = "URL has not been set";
				message.result = ReferenceMessageResult.HANDLED_FAIL;
			} else {
				responseCode = -1;
				responseData = "";
				requested = true;

				try {
					URL httpURL = new URL (url);
					HttpURLConnection httpURLConnection = (HttpsURLConnection) httpURL.openConnection ();
					httpURLConnection.setConnectTimeout (HTTP_TIMEOUT_CONNECT);
					httpURLConnection.setReadTimeout (HTTP_TIMEOUT_READ);
					httpURLConnection.setRequestMethod (method);

					for (Map.Entry <String, String> httpHeader : headers.entrySet ())
						httpURLConnection.setRequestProperty (httpHeader.getKey (), httpHeader.getValue ());

					httpURLConnection.setDoInput (true);
					httpURLConnection.setDoOutput (true);
					httpURLConnection.setInstanceFollowRedirects (true);
					httpURLConnection.setUseCaches (false);

					if (method.equals ("POST") && !postData.isEmpty ()) {
						httpURLConnection.setRequestProperty ("Content-Length", String.valueOf (postData.getBytes ().length));
						httpURLConnection.getOutputStream ().write (postData.getBytes ());
					}

					httpURLConnection.connect ();

					responseCode = httpURLConnection.getResponseCode ();

					if (DEBUG)
						logV (LOG_TAG, "Due to httpResponseCode, reading from " + (responseCode >= 200 && responseCode < 400 ? "getInputStream()" : "getErrorStream()"));

					InputStream inputStream = new BufferedInputStream (responseCode >= 200 && responseCode < 400 ? httpURLConnection.getInputStream () : httpURLConnection.getErrorStream ());
					responseData = new String (readStream (inputStream)); // Thinking about binary files

					message.value = "1";
					message.result = ReferenceMessageResult.HANDLED_OKAY;
				} catch (ProtocolException e) {
					if (DEBUG)
						logV (LOG_TAG, "Protocol exception: " + e.getMessage ());

					message.value = "Protocol exception: " + e.getMessage ();
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				} catch (IOException e) {
					if (DEBUG)
						logV (LOG_TAG, "I/O exception: " + e.getMessage ());

					message.value = "I/O exception: " + e.getMessage ();
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				} finally {
					if (DEBUG)
						logV (LOG_TAG, "HTTP response: responseCode=" + String.valueOf (responseCode) + ", responseData=" + (responseData == null ? "NULL" : new String (responseData)));
				}
			}
		} else { // Ignore anything not understood
			message.result = Kabap.ReferenceMessageResult.IGNORED;
		}

		if (DEBUG)
			logD (LOG_TAG, "OUT referenceHandler(result=" + message.result.toString () + ", value=" + message.value + ")");

		// Return the original recycled message
		return message;
	}

	/**
	 * Checks that the current network request has occurred
	 *
	 * @param message The referenceMessage object containing the net request
	 * @return True if the requested has occurred, or false otherwise
	 */
	private boolean checkNetRequest (Kabap.ReferenceMessage message) {
		if (!requested) {
			message.value = "Network request has not been made yet";
			message.result = ReferenceMessageResult.HANDLED_FAIL;
			return false;
		}

		return true;
	}

	/**
	 * Reads a stream and returns it as a byte array
	 *
	 * @param inputStream The existing stream to read
	 * @return The bytes from the stream
	 */
	public byte[] readStream (InputStream inputStream) {
		try {
			ByteArrayOutputStream bo = new ByteArrayOutputStream ();
			int i = inputStream.read ();
			while (i != -1) {
				bo.write (i);
				i = inputStream.read ();
			}

			return bo.toByteArray ();
		} catch (IOException e) {
			return "".getBytes ();
		}
	}
}
