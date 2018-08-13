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

import java.util.HashMap;

/**
 * This extension is used by UnitTesting.java to test both that the extension loading/registering mechanism works and that
 * external references can be resolved to successful message handling calls.
 *
 * @author WLD-PJ <wld-pj@kabap.org>
 * @version 1.0
 * @since 1.0
 */
public class KabapTestExtension implements Kabap.Extension {
	private static final String LOG_TAG = KabapTestExtension.class.getSimpleName ();
	private static final String REFERENCE_PREFIX = "test"; // Can be empty string to catch all unmatched references

	private boolean DEBUG = false; // Will automatically be set to true if the calling Kabap instance is in debug
	private HashMap<String, String> backingStore; // An in-memory storage pool for our testing

	/**
	 * Constructor, add some test values to the backing store
	 */
	public KabapTestExtension () {
		referenceReset ();
	}

	/**
	 * Return a string as a callback identifier, or null if the extension declines to be registered
	 *
	 * @param version The version of the calling Kabap class
	 * @param debug Whether the calling Kabap class is in debug mode and the extension should be so also
	 * @return A prefix that when used in a reference will get the Kabap processor to call-back this extension
	 */
	public String referenceRegister (int version, boolean debug) {
		// Only turn debugging on not off
		if (debug)
			DEBUG = true;

		// It is expected by v2 the Kabap extension system will be quite upgraded
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
	 * Resets the backing store
	 */
	public void referenceReset () {
		if (DEBUG)
			logD (LOG_TAG, "Resetting backing store");

		backingStore = new HashMap<String, String> ();
		backingStore.put ("instantiated", String.valueOf (System.currentTimeMillis ()));
		backingStore.put ("foo", "bar");
	}

	/**
	 * The messaging mechanism that extensions use to interface with external references
	 *
	 * @param message A message object containing all the details in the statement for the external reference request
	 * @return A message object (usually the same one) with flags to specify whether the message was successful or skipped
	 */
	public Kabap.ReferenceMessage referenceHandler (Kabap.ReferenceMessage message) {
		if (DEBUG)
			logD (LOG_TAG, " IN referenceHandler(type=" + message.type + ", name=" + message.name + ", value=" + message.value + ")");

		// Extensions are free to implement any naming convention they want, but this one uses [reference].[key]
		String messageName = message.name.toLowerCase ();
		String[] referenceName = messageName.split ("\\.");

		if (!messageName.startsWith (REFERENCE_PREFIX + ".")) {
			// If this was an actual extension it would likely just set .result = IGNORED and then return, in case another
			// extension also handles this reference prefix, but this test extension will take ownership of the prefix instead
			message.value = LOG_TAG + " requires references to start with: " + REFERENCE_PREFIX + ".";
			message.result = Kabap.ReferenceMessageResult.HANDLED_FAIL;
		} else if (referenceName.length != 2) {
			message.value = LOG_TAG + " requires references to have only 1 dot";
			message.result = Kabap.ReferenceMessageResult.HANDLED_FAIL;
		} else if (messageName.equals (REFERENCE_PREFIX + ".duplicate")) { // Unit testing test, all answer this
			message.value += "ALPHA ";
			message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
		} else if (messageName.equals (REFERENCE_PREFIX + ".duplicatea")) { // Unit testing test, answer this
			message.value = LOG_TAG;
			message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
		} else if (messageName.equals (REFERENCE_PREFIX + ".duplicateb")) { // Unit testing test, ignore this
			message.result = Kabap.ReferenceMessageResult.IGNORED;
		} else if (messageName.equals (REFERENCE_PREFIX + ".duplicatec")) { // Unit testing test, ignore this
			message.result = Kabap.ReferenceMessageResult.IGNORED;
		} else if (messageName.equals (REFERENCE_PREFIX + ".a")) { // Unit testing test, answer this
			message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
			message.value = "Pass";
		} else if (messageName.equals (REFERENCE_PREFIX + ".immutable")) { // Unit testing test, answer this
			if (message.type == Kabap.ReferenceMessageType.WRITE) {
				message.value = REFERENCE_PREFIX + ".immutable is immutable";
				message.result = Kabap.ReferenceMessageResult.HANDLED_FAIL;
			} else { // The value is frozen
				message.value = "let it=go;";
				message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
			}
		} else {
			if (message.type == Kabap.ReferenceMessageType.READ) { // Read is from the perspective of the caller not the callee
				message.value = backingStore.get (referenceName[1]);
				if (message.value == null) // Kabap does not deal with null, only empty strings
					message.value = "";
				message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
			} else if (message.type == Kabap.ReferenceMessageType.WRITE) { // Write is from the perspective of the caller not the callee
				backingStore.put (referenceName[1], message.value);
				message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
			} else { // Currently only READ and WRITE types are supported, so catch anything unexpected
				message.value = "Message type is not READ|WRITE?!";
				message.result = Kabap.ReferenceMessageResult.HANDLED_FAIL;
			}
		}

		if (DEBUG)
			logD (LOG_TAG, "OUT referenceHandler(result=" + message.result.toString() + ", value=" + message.value + ")");

		// Returning the original recycled message
		return message;
	}
}
