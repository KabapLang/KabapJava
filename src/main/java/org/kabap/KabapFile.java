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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.kabap.Kabap.Extension;
import org.kabap.Kabap.ReferenceMessage;
import org.kabap.Kabap.ReferenceMessageResult;
import org.kabap.Kabap.ReferenceMessageType;

/**
 * This extension provides local filesystem access; it is considered DANGEROUS.
 * <p>
 * <b>WARNING:</b> If you execute Kabap with this extension in a security context higher than that of the user
 * then they can use it to gain privilege escalation. For example, running the Kabap engine as root (which is
 * also something you should NEVER do) lets a non-root user modify /etc/passwd because filesystem operations
 * will have the access level of whomever owns the running process (root).
 * </p>
 *
 * @author WLD-PJ <wld-pj@kabap.org>
 * @version 1.0
 * @since 1.0
 */
public class KabapFile implements Extension {
	private static final String LOG_TAG = KabapFile.class.getSimpleName ();
	private static final String REFERENCE_PREFIX = "file";
	private static final String ESCAPE_SEQUENCE = "__!*DBLBCKSLSH()__";

	private boolean DEBUG = false;
	private ArrayList<KabapFileEntry> files = null;
	private int filePointer;

	/** Defines a Kabap file descriptor */
	private final class KabapFileEntry {
		boolean escaped = false;
		boolean created;
		File file;
	}

	/**
	 * Constructor, reset the file list
	 */
	public KabapFile () {
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
	 * Reset the file list
	 */
	public void referenceReset () {
		if (DEBUG)
			logV (LOG_TAG, "File list reset");

		files = new ArrayList<KabapFileEntry> ();
		filePointer = -1;
	}

	/**
	 * Handle file operation messages, by default this extension skips unknown requests
	 */
	public ReferenceMessage referenceHandler (ReferenceMessage message) {
		if (DEBUG)
			logV (LOG_TAG, " IN referenceHandler(type=" + message.type + ", name=" + message.name + ", value=" + message.value + ")");

		// This extensions implements naming convention [reference].[key]
		String[] nameParts = message.name.toLowerCase ().split ("\\.");

		if (nameParts.length != 2) { // Ignoring this request as it is not the right naming convention
			message.result = Kabap.ReferenceMessageResult.IGNORED;
		} else if (nameParts[1].equals ("handle")) {
			if (message.type == ReferenceMessageType.READ) {
				message.value = String.valueOf (filePointer + 1);
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			} else {
				int filePointerNew = 0;
				try {
					filePointerNew = Integer.valueOf (message.value);
				} catch (NumberFormatException e) {
				}

				if (filePointerNew <= 0 || filePointerNew > files.size () || files.get (filePointerNew - 1) == null) {
					message.value = "File handle invalid";
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				} else {
					filePointer = --filePointerNew;
					message.result = ReferenceMessageResult.HANDLED_OKAY;
				}
			}
		} else if (nameParts[1].equals ("escape")) {
			if (checkFileHandle (message)) {
				if (message.type == ReferenceMessageType.READ) {
					message.value = files.get (filePointer).escaped ? "1" : "0";
				} else {
					files.get (filePointer).escaped = !message.value.equals ("0");
				}

				message.result = ReferenceMessageResult.HANDLED_OKAY;
			}
		} else if (nameParts[1].equals ("isnew") && message.type == ReferenceMessageType.READ) {
			if (checkFileHandle (message)) {
				message.value = files.get (filePointer).created ? "1" : "0";
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			}
		} else if (nameParts[1].equals ("size") && message.type == ReferenceMessageType.READ) {
			if (checkFileHandle (message)) {
				message.value = String.valueOf (files.get (filePointer).file.length ());
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			}
		} else if (nameParts[1].equals ("open") && message.type == ReferenceMessageType.WRITE) {
			String path = message.value.trim ();

			if (path.isEmpty ()) {
				message.value = "Filename cannot be empty";
				message.result = ReferenceMessageResult.HANDLED_FAIL;
			} else {
				KabapFileEntry kabapFileEntry = new KabapFileEntry ();

				kabapFileEntry.file = new File (path);

				boolean success = false;
				try {
					if (kabapFileEntry.file.exists ()) {
						kabapFileEntry.created = false;
						if (!kabapFileEntry.file.canRead ()) {
							message.value = "Read permission denied";
						} else if (!kabapFileEntry.file.isFile ()) {
							message.value = "Path is not a file";
						} else {
							success = true;
						}
					} else {
						kabapFileEntry.created = true;
						if (!kabapFileEntry.file.createNewFile ()) {
							message.value = "Create permission denied";
						} else {
							success = true;
						}
					}
				} catch (Exception e) {
					message.value = e.getMessage ();
				}

				if (!success) {
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				} else {
					files.add (kabapFileEntry);
					filePointer = files.size () - 1;
					message.result = ReferenceMessageResult.HANDLED_OKAY;
				}
			}
		} else if (nameParts[1].equals ("close") && message.type == ReferenceMessageType.READ) {
			if (checkFileHandle (message)) {
				files.set (filePointer, null);
				message.value = "1";
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			}
		} else if (nameParts[1].equals ("delete") && message.type == ReferenceMessageType.READ) {
			if (checkFileHandle (message)) {
				KabapFileEntry kabapFileEntry = files.get (filePointer);
				try {
					if (kabapFileEntry.file.delete ()) {
						files.set (filePointer, null);
						message.value = "1";
						message.result = ReferenceMessageResult.HANDLED_OKAY;
					} else {
						message.value = "Unable to delete file";
						message.result = ReferenceMessageResult.HANDLED_FAIL;
					}
				} catch (Exception e) {
					message.value = e.getMessage ();
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				}
			}
		} else if (nameParts[1].equals ("read") && message.type == ReferenceMessageType.READ) {
			if (checkFileHandle (message)) {
				message.value = fileRead (files.get (filePointer).file);
				if (message.value == null) {
					message.value = "File count not be read";
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				} else {
					message.result = ReferenceMessageResult.HANDLED_OKAY;
				}
			}
		} else if ((nameParts[1].equals ("write") || nameParts[1].equals ("append")) && message.type == ReferenceMessageType.WRITE) {
			if (checkFileHandle (message)) {
				if (files.get (filePointer).escaped)
					message.value = message.value.replace ("\\\\", ESCAPE_SEQUENCE).replace ("\\n", "\n").replace ("\\r", "\r").replace ("\\t", "\t").replace (ESCAPE_SEQUENCE, "\\");

				String error = fileWrite (files.get (filePointer).file, nameParts[1].equals ("append"), message.value);
				if (error == null) {
					message.result = ReferenceMessageResult.HANDLED_OKAY;
				} else {
					message.value = error;
					message.result = ReferenceMessageResult.HANDLED_FAIL;
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
	 * Checks that the current filePointer handle is open and valid
	 *
	 * @param message The referenceMessage object containing the file request
	 * @return True if the handle is valid, or false otherwise
	 */
	private boolean checkFileHandle (Kabap.ReferenceMessage message) {
		if (filePointer == -1) {
			message.value = "File not opened";
			message.result = ReferenceMessageResult.HANDLED_FAIL;
			return false;
		} else if (files.get (filePointer) == null) {
			message.value = "File already closed";
			message.result = ReferenceMessageResult.HANDLED_FAIL;
			return false;
		}

		return true;
	}

	/**
	 * Reads a file from disk and returns it as a string
	 *
	 * @param file The file to read
	 * @return String containing the file contents, or null if file cannot be read
	 */
	private static String fileRead (File file) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream ();

		try {
			InputStream inputStream = new FileInputStream (file);
			if (inputStream == null)
				return null;

			byte buf[] = new byte[1024];
			int len;

			while ((len = inputStream.read (buf)) != -1)
				outputStream.write (buf, 0, len);

			outputStream.close ();
			inputStream.close ();
		} catch (IOException e) {
			e.printStackTrace ();
		}

		return outputStream.toString ();
	}

	/**
	 * Writes a string to a file on disk
	 *
	 * @param file The file to write
	 * @return NULL if write was successful or error message on failure
	 */
	private static String fileWrite (File file, boolean append, String content) {
		try {
			OutputStream outputStream = new FileOutputStream (file, append);
			outputStream.write (content.getBytes ());
			outputStream.flush ();
			outputStream.close ();

			return null;
		} catch (IOException e) {
			return e.toString ();
		}
	}
}
