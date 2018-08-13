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

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Kabap for Java
 *
 * @author WLD-PJ <wld-pj@kabap.org>
 * @version 1.0
 * @since 1.0
 */
public class Kabap {
	// Public information
	public static final int VERSION_MAJOR = 1;
	public static final int VERSION_MINOR = 0;

	// Internal settings
	private static final String LOG_TAG = Kabap.class.getSimpleName ();
	private static final boolean DEBUG = false;

	// Script defaults
	private static final int watchdogTickLimitDefault = 1000;
	private static final int decimalScaleDefault = 3;

	private int watchdogTickLimit = watchdogTickLimitDefault;
	private int decimalScale = decimalScaleDefault;
	private DecimalFormat decimalFormat;

	// Engine internals
	private ArrayList<ArrayList> kabapTokens = null;
	private ArrayList<TokenEntry> statementTokens = null;
	private HashMap<String, Integer> kabapLabels = null;
	private HashMap<String, String> kabapVariables = null;
	private HashMap<String, ArrayList<Extension>> kabapExtensions = null;
	private TokenType tokenType = TokenType.NULL;
	private TokenEntry tokenEntry = null;
	private String kabapScript = null;
	private int lineNumber = 0;
	private int tokenBlockNests = 0;

	/** Execution normal output, populated with <code>return =</code> */
	public String stdout = "";

	/** Execution error output, populated with {@link #error(String)} */
	public String stderr = "";

	/** Holds a token, the lowest unit of execution */
	private final class TokenEntry {
		TokenType type = null;
		String value = null;
	}

	/** Token types the engine understands */
	private static enum TokenType {
		NULL,
		WHITESPACE,
		COMMENT,
		LINEHINT,
		STATEMENTEND,
		BLOCKSTART,
		BLOCKEND,
		FLOW,
		OPERATOR,
		VARIABLE,
		STRING,
		NUMBER,
		REFERENCE,
		LABEL
	}

	/** Keywords for program flow control */
	private static final String[] tokenFlow = {
		"break",
		"goto",
		"if"
	};

	/** Operators for comparisons */
	private static final String[] operatorComparator = {
		"<",  // Less than
		"<=", // Less than or equal to
		"==", // Equal to
		">=", // Greater than or equal to
		">",  // Greater than
		"!="  // Not equal to
	};

	/** Operators for calculations */
	private static final String[] operatorMathematical = {
		"+",  // Addition
		"-",  // Subtraction
		"*",  // Multiplication
		"/",  // Division
		"%",  // Modulo
		"^",  // Power
		"++", // Increment
		"--"  // Decrement
	};

	/** Operators for writing values */
	private static final String[] operatorAssignment = {
		"="  // Equals
	};

	/** Operators for string manipulation */
	private static final String[] operatorString = {
		"<<"  // Concatenate
	};

	/** Types of messages used in {@link ReferenceMessage}, from the perspective of the caller not callee */
	public static enum ReferenceMessageType {
		READ,
		WRITE
	}

	/** Result status of message used in {@link ReferenceMessage} */
	public static enum ReferenceMessageResult {
		IGNORED,
		HANDLED_OKAY,
		HANDLED_FAIL
	}

	/** The reference message class passed between the Kabap engine and {@link KabapExtension} */
	public final class ReferenceMessage {
		/** The type of message, such as a READ or WRITE (from the perspective of the caller not the callee) **/
		ReferenceMessageType type;

		/** The result of the message after processing, use IGNORED to continue processing or return a result */
		ReferenceMessageResult result;

		/** The full name of the reference, typically the registered reference handler, a dot, and a method name */
		String name;

		/** The value for the reference, if a write contains the RValue, if a read, populate for the LValue **/
		String value;

		/** A custom object usable for inter-extension communication; left as an exercise for the implementation */
		Object custom;

		ReferenceMessage (ReferenceMessageType type, String name, String value) {
			this.type = type;
			this.name = name;
			this.value = (value == null ? "" : value);
		}
	}

	/**
	 * The Kabap interface to register and and use extensions
	 */
	public interface Extension {
		/**
		 * Extension registration, used to set the extension prefix (or decline to register)
		 *
		 * @param version Kabap engine version number which is sent to the extension
		 * @param debug A flag as to whether the Kabap engine is in debug mode
		 * @return A string for the external reference callback, or null to decline to be registered
		 */
		String referenceRegister (int version, boolean debug);

		/**
		 * Extension referenceReset, used when {@link #reset()} is called to clear extension state
		 */
		void referenceReset ();

		/**
		 * Extension messaging, the mechanism used to read/call/write to the user script
		 *
		 * @param message A ReferenceMessage object passed between the extension and Kabap engine
		 * @return A ReferenceMessage object (usually the same one) containing any result
		 */
		ReferenceMessage referenceHandler (ReferenceMessage message);
	}

	/**
	 * The default internal Kabap extension
	 * <p>Currently provides just the version number, the current scale and a random number generator
	 * to begin with but with plans to extend the functionality in the future.</p>
	 */
	private class KabapExtension implements Extension {
		public String referenceRegister (int version, boolean debug) {
			return Kabap.class.getSimpleName ().toLowerCase ();
		}

		public void referenceReset () {
		}

		public ReferenceMessage referenceHandler (ReferenceMessage message) {
			String[] nameParts = message.name.toLowerCase ().split ("\\.");

			if (nameParts.length != 2) {
				message.result = ReferenceMessageResult.IGNORED;
			} else if (nameParts[1].equals ("version")) {
				if (message.type == ReferenceMessageType.READ) {
					message.value = VERSION_MAJOR + "." + VERSION_MINOR;
					message.result = ReferenceMessageResult.HANDLED_OKAY;
				} else {
					message.value = message.name + " is read only";
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				}
			} else if (nameParts[1].equals ("scale")) {
				if (message.type == ReferenceMessageType.WRITE) {
					scaleSet ((int) numberExtract (message.value, -1));
				} else if (message.type == ReferenceMessageType.READ) {
					message.value = String.valueOf (decimalScale);
				}
				message.result = ReferenceMessageResult.HANDLED_OKAY;
			} else if (nameParts[1].equals ("random")) {
				if (message.type == ReferenceMessageType.READ) {
					message.value = String.valueOf (new Random ().nextInt (10000));
					message.result = ReferenceMessageResult.HANDLED_OKAY;
				} else {
					message.value = message.name + " is read only";
					message.result = ReferenceMessageResult.HANDLED_FAIL;
				}
			} else {
				message.result = ReferenceMessageResult.IGNORED;
			}

			return message;
		}
	}

	/**
	 * Constructor, prepare extensions
	 */
	public Kabap () {
		if (DEBUG)
			logD (LOG_TAG, "Instantiating Kabap");

		extensionRemoveAll ();
	}

	/**
	 * Gets the watchdog tick limit
	 *
	 * @return Number of ticks
	 */
	public int watchdogGet () {
		return watchdogTickLimit;
	}

	/**
	 * Sets the watchdog tick limit
	 *
	 * @param watchdogTickLimit Number of ticks
	 */
	public void watchdogSet (int watchdogTickLimit) {
		this.watchdogTickLimit = (watchdogTickLimit < 0 ? watchdogTickLimitDefault : watchdogTickLimit);

		if (DEBUG)
			logD (LOG_TAG, "Set watchdog tick limit=" + this.watchdogTickLimit);
	}

	/**
	 * Resets instance between executions of the same script
	 */
	public void reset () {
		lineNumber = 0; // Set to 0 to avoid showing in error logs
		stdout = stderr = ""; // Reset stdout and stderr to empty
		variableRemoveAll (); // Remove all variables

		// Call {@link Extension#referenceReset()} on all extensions
		for (Map.Entry<String, ArrayList<Extension>> entry : kabapExtensions.entrySet ()) {
			ArrayList<Extension> extensions = entry.getValue ();
			for (int i = 0, j = extensions.size (); i < j; ++i)
				extensions.get (i).referenceReset ();
		}

		if (DEBUG)
			logD (LOG_TAG, "Kabap engine reset");
	}

	/**
	 * Returns whether a variable is set
	 *
	 * @param key Name of variable (sans $)
	 * @return True if the variable is set, or false if not
	 */
	public boolean variableHas (String key) {
		return kabapVariables.containsKey (key);
	}

	/**
	 * Gets a variable
	 *
	 * @param key Name of variable (sans $)
	 * @return Variable content or null if variable is not set
	 */
	public String variableGet (String key) {
		return kabapVariables.get (key);
	}

	/**
	 * Sets a variable
	 *
	 * @param key Name of variable (sans $)
	 * @param value Content of variable
	 */
	public void variableSet (String key, String value) {
		if (DEBUG)
			logV (LOG_TAG, "Setting variable: " + key + "=" + value);

		kabapVariables.put (key, value);
	}

	/**
	 * Sets a variable
	 *
	 * @param key Name of variable (sans $)
	 * @param value Content of variable
	 */
	public void variableSet (String key, int value) {
		variableSet (key, String.valueOf (value));
	}

	/**
	 * Sets a variable
	 *
	 * @param key Name of variable (sans $)
	 * @param value Content of variable
	 */
	public void variableSet (String key, long value) {
		variableSet (key, String.valueOf (value));
	}

	/**
	 * Sets a variable
	 *
	 * @param key Name of variable (sans $)
	 * @param value Content of variable
	 */
	public void variableSet (String key, float value) {
		variableSet (key, String.valueOf (value));
	}

	/**
	 * Sets a variable
	 *
	 * @param key Name of variable (sans $)
	 * @param value Content of variable
	 */
	public void variableSet (String key, double value) {
		variableSet (key, String.valueOf (value));
	}

	/**
	 * Unset a variable
	 *
	 * @param key Name of variable (sans $)
	 */
	public void variableRemove (String key) {
		if (DEBUG)
			logV (LOG_TAG, "Removing variable: " + key);

		kabapVariables.remove (key);
	}

	/**
	 * Unsets all variables
	 */
	public void variableRemoveAll () {
		if (DEBUG)
			logV (LOG_TAG, "Removing all variables");

		kabapVariables = new HashMap<String, String> ();
	}

	/**
	 * Retrieves the complete variables store
	 */
	public HashMap<String, String> variableStoreGet () {
		return kabapVariables;
	}

	/**
	 * Stores the complete variable store
	 *
	 * @param kabapVariables HashMap of Key->Value pairs, where key is variable name (sans $)
	 */
	public void variableStoreSet (HashMap<String, String> kabapVariables) {
		if (DEBUG)
			logV (LOG_TAG, "Setting variable store with " + kabapVariables.size () + " variables");

		this.kabapVariables = kabapVariables;
	}

	/**
	 * Gets the decimal scale for mathematical operations
	 *
	 * @return Number of decimal places used
	 */
	public int scaleGet () {
		return decimalScale;
	}

	/**
	 * Sets the decimal scale for mathematical operations
	 *
	 * @param decimalScale Number of decimal places to use
	 */
	public void scaleSet (int decimalScale) {
		if (decimalScale < 0)
			decimalScale = decimalScaleDefault;

		this.decimalScale = decimalScale;

		// Set the new formatting mask and rounding properties
		decimalFormat = new DecimalFormat ("#" + (decimalScale > 0 ? "." + new String (new char[decimalScale]).replace ("\0", "#") : ""));
		decimalFormat.setRoundingMode (RoundingMode.HALF_UP);

		if (DEBUG)
			logD (LOG_TAG, "Set decimal scale=" + decimalScale + "; pattern=" + decimalFormat.toLocalizedPattern ());
	}

	/**
	 * Requests the addition of an extension to Kabap, automatically initiates the registration process
	 * <p>If the extension is an anonymous closure it cannot be removed with {@link #extensionRemove(Extension)}
	 * and instead you will need to use {@link #extensionRemoveAll()}.</p>
	 *
	 * @param extension A Kabap.Extension instance
	 * @return True if the extension was successfully registered, or false if not
	 */
	public boolean extensionAdd (Extension extension) {
		if (DEBUG)
			logD (LOG_TAG, "Adding extension: " + (extension.getClass ().isAnonymousClass () ? "[anonymous closure]" : extension.getClass ().getSimpleName ()));

		// Check if the extension is already added (if it is a named class)
		if (!extension.getClass ().isAnonymousClass ()) {
			for (Map.Entry<String, ArrayList<Extension>> entry : kabapExtensions.entrySet ()) {
				ArrayList<Extension> extensions = entry.getValue ();
				for (int i = 0, j = extensions.size (); i < j; ++i) {
					if (extensions.get (i).getClass () == extension.getClass ()) {
						if (DEBUG)
							logD (LOG_TAG, "Extension is already added so skipping");

						return false;
					}
				}
			}
		}

		// Get the registration reference
		String registeredReference = extension.referenceRegister (VERSION_MAJOR, DEBUG);

		if (registeredReference == null) { // A null reference means the extension declined to be added
			if (DEBUG)
				logD (LOG_TAG, "Extension chose not to be added");

			return false;
		} else if (registeredReference.isEmpty ()) { // An empty reference means the extension is catchall
			registeredReference = "*";
		}

		if (!kabapExtensions.containsKey (registeredReference.toLowerCase ()))
			kabapExtensions.put (registeredReference.toLowerCase (), new ArrayList<Extension> ());

		kabapExtensions.get (registeredReference.toLowerCase ()).add (extension);

		if (DEBUG)
			logD (LOG_TAG, "Extension added with reference: " + registeredReference);

		return true;
	}

	/**
	 * Removes an extension from Kabap
	 * <p>If the extension to be removed was added as an anonymous closure it cannot be removed with this method,
	 * call {@link #extensionRemoveAll()} instead. Anonymous closures lack the class name Java uses to compare.</p>
	 *
	 * @param extension Kabap.Extension instance
	 * @return True if the extension was found and removed, or false if not
	 */
	public boolean extensionRemove (Extension extension) {
		if (DEBUG)
			logD (LOG_TAG, "Removing extension: " + (extension.getClass ().isAnonymousClass () ? "[anonymous closure] has no reference to the original object" : extension.getClass ().getSimpleName ()));

		boolean removed = false;

		if (!extension.getClass ().isAnonymousClass ()) {
			String[] registeredReferences = kabapExtensions.keySet ().toArray (new String[0]);
			for (String registeredReference : registeredReferences) {
				for (int i = kabapExtensions.get (registeredReference).size () - 1; i > -1; --i) {
					if (kabapExtensions.get (registeredReference).get (i).getClass () == extension.getClass ()) {
						kabapExtensions.get (registeredReference).remove (i);
						removed = true;
						break;
					}
				}

				// If that was the last extension for a reference, remove the reference also
				if (kabapExtensions.get (registeredReference).size () == 0)
					kabapExtensions.remove (registeredReference);
			}
		} else {
			error ("Anonymous closure extensions cannot be removed");
		}

		if (DEBUG)
			logD (LOG_TAG, "Extension was " + (removed ? "" : "NOT ") + "removed");

		return removed;
	}

	/**
	 * Removes all extensions from Kabap (re-adds the internal extension afterwards)
	 */
	public void extensionRemoveAll () {
		if (DEBUG)
			logD (LOG_TAG, "Removing all extensions");

		kabapExtensions = new HashMap<String, ArrayList <Extension>> ();
		extensionAdd (new KabapExtension ());
	}

	/**
	 * Bypass the overhead of {@link #script(String)} and load in pre-parsed tokens for reduced time-to-execution
	 *
	 * @param tokens String of tokens in the condensed token format (currently mapped to {@link #VERSION_MAJOR})
	 * @return True on success, or false on failure (here the focus is entirely on speed and not robustness)
	 */
	public boolean tokensLoad (String tokens) {
		if (DEBUG)
			logD (LOG_TAG, "tokensLoad()" + (tokens == null ? " no tokens passed, returning false" : ""));

		// Ensure the string is a string
		if (tokens == null)
			return false;

		if (DEBUG)
			logV (LOG_TAG, "Tokenised format:\n" + tokens);

		// Prepare storage for what is about to be received
		kabapScript = null;
		kabapTokens = new ArrayList<ArrayList> ();
		statementTokens = new ArrayList<TokenEntry> ();
		kabapLabels = new HashMap<String, Integer> ();

		// Split the string by UNIX line endings and ensure the first line is a comment or fail fast
		String[] tokenLines = tokens.split ("\n");
		if (!tokenLines[0].startsWith ("//"))
			return false;

		// Process the first line comment (what should be the header line) to extract predicates from split chunks
		String[] tokenLineHeader = tokenLines[0].split (" ");
		HashMap<String, String> kabapPredicates = new HashMap<String, String> ();
		for (int i = 0, j = tokenLineHeader.length; i < j; ++i) {
			String[] kabapPredicate = tokenLineHeader[i].split ("=");
			// Populate any predicates if this is a good candidate
			if (kabapPredicate.length == 2)
				kabapPredicates.put (kabapPredicate[0], kabapPredicate[1]);
		}

		// Fail fast if any predicates are missing or invalid (may not actually be a .kat file or is corrupt)
		if (!kabapPredicates.containsKey ("Kabap") || !kabapPredicates.get ("Kabap").equals ("Tokens") || !kabapPredicates.containsKey ("v") || Integer.valueOf (kabapPredicates.get ("v")) < 1 || Integer.valueOf (kabapPredicates.get ("v")) > VERSION_MAJOR || !kabapPredicates.containsKey ("utf8") || !kabapPredicates.get ("utf8").equals ("✓"))
			return false;

		// Reset the engine and set any defaults specified in the header line
		reset ();
		scaleSet (kabapPredicates.containsKey ("s") ? Integer.valueOf (kabapPredicates.get ("s")) : -1);
		watchdogSet (kabapPredicates.containsKey ("wd") ? Integer.valueOf (kabapPredicates.get ("wd")) : -1);

		// Begin token extraction
		char c;
		boolean lastImmediate = false, thisImmediate = false;
		for (int i = lineNumber = 1, j = tokenLines.length; i <= j; ++i) { // Read past last line to ensure it is processed
			++lineNumber; // For general error messages, not line hints

			// Read the token type hint or set to STATEMENTEND if a faux read
			c = (i < j ? tokenLines[i].charAt (0) : ';');

			// Skip comments
			if (c == '/')
				continue;

			// Create a token entry then populate its type and value
			tokenEntry = new TokenEntry ();

			tokenEntry.type = (c == '.' ? TokenType.LINEHINT : (c == ';' ? TokenType.STATEMENTEND : (c == '{' ? TokenType.BLOCKSTART : (c == '}' ? TokenType.BLOCKEND : (c == '>' ? TokenType.FLOW : (c == '_' ? TokenType.OPERATOR : (c == '$' ? TokenType.VARIABLE : (c == '"' ? TokenType.STRING : (c == '#' ? TokenType.NUMBER : (c == '@' ? TokenType.REFERENCE : (c == ':' ? TokenType.LABEL : TokenType.NULL)))))))))));

			if (i < j && tokenLines[i].length () > 1) { // Only multi-symbol tokens need values reading
				tokenEntry.value = tokenLines[i].substring (1);
			} else if (c == '"') { // Single-symbol STRING tokens are always empty never null
				tokenEntry.value = "";
			}

			// Determine if the token qualifies for an immediate read and append the statement if so
			thisImmediate = (c == ';' || c == '.' || c == ':' || c == '{' || c == '}');
			if ((thisImmediate || lastImmediate || i == j) && statementTokens.size () > 0) {
				kabapTokens.add (statementTokens);
				statementTokens = new ArrayList<TokenEntry> ();

				// Store location of LABEL tokens
				if (c == ':')
					kabapLabels.put (tokenEntry.value, kabapTokens.size ());
			}

			// Add the token to the current statement (ignore a STATEMENTEND)
			if (c == ';') {
				lastImmediate = false;
			} else {
				lastImmediate = thisImmediate;
				statementTokens.add (tokenEntry);
			}
		}

		if (DEBUG)
			debugDumpTokens ();

		return true;
	}

	/**
	 * Save tokenised script in the pre-parsed format for quicker future use with {@link #tokensLoad(String)}
	 *
	 * @param optimiseLevel Various optimisations and minification to reduce output size and future load time
	 * @return String of tokens in the condensed token format (currently mapped to {@link #VERSION_MAJOR})
	 */
	public String tokensSave (int optimiseLevel) {
		if (DEBUG)
			logD (LOG_TAG, "tokensSave(optimiseLevel=" + optimiseLevel + ")" + (kabapTokens == null ? " no script loaded, returning null" : ""));

		// Ensure a script or tokens have already been loaded
		if (kabapTokens == null) {
			error ("No script or tokens have yet been loaded");
			return null;
		}

		// Perform optimisation to the desired level
		if (!optimise (optimiseLevel))
			return null;

		// Generate the required header line comment
		StringBuilder sb = new StringBuilder ();
		sb.append ("// Kabap=Tokens v=").append (VERSION_MAJOR).append (" utf8=✓ s=").append (decimalScale).append (" wd=").append (watchdogTickLimit).append (" o=").append (optimiseLevel).append (" e=");
		Set<String> extensionNames = kabapExtensions.keySet (); // Get the list of currently loaded extensions
		extensionNames.remove (Kabap.class.getSimpleName ().toLowerCase ()); // Hide the default Kabap extension from the list
		sb.append (extensionNames.toString ().replaceAll ("[\\[\\] ]", ""));
		sb.append ("\n");

		// Start appending tokens
		boolean lastImmediate = false, thisImmediate = false;
		for (int i = 0, j = kabapTokens.size (); i < j; ++i) {
			for (int k = 0, l = kabapTokens.get (i).size (); k < l; ++k) {
				// Get the token and determine if it qualifies for immediate write
				tokenEntry = (TokenEntry) kabapTokens.get (i).get (k);
				thisImmediate = (tokenEntry.type == TokenType.LINEHINT || tokenEntry.type == TokenType.LABEL || tokenEntry.type == TokenType.BLOCKSTART || tokenEntry.type == TokenType.BLOCKEND);

				// Write separator only between two non-immediate tokens
				if (k == 0 && !lastImmediate && !thisImmediate)
					sb.append (";\n");
				lastImmediate = thisImmediate;

				// Write the type symbol
				sb.append (tokenEntry.type == TokenType.LINEHINT ? '.' : (tokenEntry.type == TokenType.STATEMENTEND ? ';' : (tokenEntry.type == TokenType.BLOCKSTART ? '{' : (tokenEntry.type == TokenType.BLOCKEND ? '}' : (tokenEntry.type == TokenType.FLOW ? '>' : (tokenEntry.type == TokenType.OPERATOR ? '_' : (tokenEntry.type == TokenType.VARIABLE ? '$' : (tokenEntry.type == TokenType.STRING ? '"' : (tokenEntry.type == TokenType.NUMBER ? '#' : (tokenEntry.type == TokenType.REFERENCE ? '@' : (tokenEntry.type == TokenType.LABEL ? ':' : 0)))))))))));

				// Write the value only if it has one (a multi-symbol token)
				if (tokenEntry.value != null)
					sb.append (tokenEntry.value);

				sb.append ("\n");
			}
		}

		// Ensure there are no trailing newlines
		while (sb.charAt (sb.length () - 1) == '\n')
			sb.setLength (sb.length () - 1);

		if (DEBUG)
			logV (LOG_TAG, "Tokenised format:\n" + sb.toString ());

		return sb.toString ();
	}

	/**
	 * Loads, parses, tokenises and optimises a Kabap script from a string, but does not execute it (see {@link #run()})
	 *
	 * @param kabapScript A (hopefully) valid Kabap script
	 * @return True if the script is ready for execution, or false if it is invalid (stderr will contain the error reason)
	 */
	public boolean script (String kabapScript) {
		if (DEBUG) {
			if (kabapScript == null) {
				logD (LOG_TAG, "No input script passed");
			} else {
				logD (LOG_TAG, "Input script was passed");
			}
		}

		if (kabapScript != null) {
			// Remove any UTF BOM
			if (kabapScript.startsWith ("\uFEFF"))
				kabapScript = kabapScript.substring (1);

			// UNIX-ify any line endings
			kabapScript = kabapScript.replaceAll ("\r\n", "\n").replaceAll ("\r", "\n");

			if (DEBUG)
				logV (LOG_TAG, "\n" + kabapScript);

			int i = kabapScript.length ();
			if (kabapScript.substring (0, i < 64 ? i : 64).toLowerCase ().contains ("kabap=tokens"))
				return error ("Cannot load tokens as a script");
		}

		this.kabapScript = kabapScript;

		// Reset engine back to defaults
		reset ();
		scaleSet (decimalScaleDefault);
		watchdogSet (watchdogTickLimitDefault);

		try {
			return tokenise () && optimise (1);
		} catch (Exception e) {
			if (DEBUG)
				e.printStackTrace ();

			return error ("Tokeniser crash: " + e.toString ());
		}
	}

	/**
	 * Runs the script which was last loaded via {@link #script(String)} or from tokens via {@link #tokensLoad(String)}
	 * <p>Be mindful that the state of the execution engine is NOT reset on successive calls, even if the script or the
	 * tokens have changed. Variables and extensions will persist across calls, so use {@link #reset()} to clear variables
	 * and {@link #extensionRemoveAll()} to clear extensions if needed. This will enable the instance of Kabap to be fully
	 * recycled for multiple uses.</p>
	 *
	 * @return True if executed successfully, or false if there was an error (stderr will contain the error reason)
	 */
	public boolean run () {
		try {
			return execute ();
		} catch (Exception e) {
			if (DEBUG)
				e.printStackTrace ();

			return error ("Executor crash: " + e.toString ());
		}
	}

	/**
	 * Step 1: Parsing/Tokenisation
	 * <p>Convert the script to normalised tokens, even if at this stage the order or syntax of the tokens makes no
	 * sense (for example, opening a block where an operand is expected). Only basic sanity checks are done at this
	 * stage, including making sure newlines are not encountered before any non-WHITESPACE token is being parsed and
	 * making sure BLOCKEND is only encountered when there is a matching BLOCKSTART. Unexpected characters are reported,
	 * and all COMMENTs and WHITESPACE are stripped out. The parsed tokens are saved for faster repeated execution and
	 * the original script text is cleared to save memory.</p>
	 */
	private boolean tokenise () {
		// Prepare token and label storage
		kabapTokens = new ArrayList<ArrayList> ();
		statementTokens = new ArrayList<TokenEntry> ();
		kabapLabels = new HashMap<String, Integer> ();

		// If no script is loaded, fail early
		if (kabapScript == null)
			return false;

		// Working storage
		TokenType tokenTypePrevious = TokenType.NULL;
		TokenType tokenTypeNext = TokenType.NULL;
		String tokenValue = "";
		boolean tokenFlowConditional = false;
		int tokenCountKabap = 0;
		int tokenCountStatement = 0;
		char c;

		for (int i = -1, j = kabapScript.length (); j > 0 && i <= j; ++i) { // Read pre/post 1 char to process full buffer
			// Set the faux char to NUL when out of bounds
			c = (i > -1 && i < j ? kabapScript.charAt (i) : 0);

			// The last faux read token is (ignored) whitespace
			if (i == j)
				tokenTypeNext = TokenType.WHITESPACE;

			if (DEBUG) {
				logV (LOG_TAG, "" + c);
				logV (LOG_TAG, "	START tokenType=" + tokenType + "	tokenTypeNext=" + tokenTypeNext);
			}

			// Work out what the token type is, or append its value if already in a token mode
			if (c == '\n' || i == -1 || i == j) { // After newline, and first and last iterations
				if (tokenType == TokenType.COMMENT) {
					tokenType = TokenType.STATEMENTEND;
				} else if (tokenType != TokenType.NULL && tokenType != TokenType.WHITESPACE && tokenType != TokenType.LINEHINT && tokenType != TokenType.STATEMENTEND && tokenType != TokenType.BLOCKSTART && tokenType != TokenType.BLOCKEND) {
					return error ("Unterminated " + (tokenType == TokenType.REFERENCE && tokenCountStatement > 0 && (statementTokens.get (tokenCountStatement - 1).type == TokenType.LABEL || statementTokens.get (tokenCountStatement - 1).type == TokenType.VARIABLE) ? statementTokens.get (tokenCountStatement - 1).type.toString ().toLowerCase () : tokenType.toString ().toLowerCase ()));
				}

				if (c == '\n' || i == -1)
					tokenTypeNext = TokenType.LINEHINT;
				c = 0;
			} else if (tokenType == TokenType.COMMENT) {
				continue;
			} else if (c == '"' || tokenType == TokenType.STRING) {
				if (tokenType != TokenType.STRING) {
					tokenTypeNext = TokenType.STRING;
					c = 0;
				} else if (c == '"') {
					tokenTypeNext = TokenType.WHITESPACE;
				}
			} else if (c == ' ' || c == '	' || c == 0) {
				if (tokenType != TokenType.NULL)
					tokenTypeNext = TokenType.WHITESPACE;
			} else if (c == '{') {
				tokenTypeNext = TokenType.BLOCKSTART;
				++tokenBlockNests;
			} else if (c == '}') {
				tokenTypeNext = TokenType.BLOCKEND;
				if (--tokenBlockNests < 0)
					return error ("Closing unopened block");
			} else if (c == '<' || c == '=' || c == '>' || c == '!' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^') {
				if (c == '/' && tokenValue.equals ("/")) {
					tokenType = TokenType.NULL;
					tokenTypeNext = TokenType.COMMENT;
					c = 0;
					tokenValue = "";
				} else {
					if (tokenType != TokenType.OPERATOR)
						tokenTypeNext = TokenType.OPERATOR;
				}
			} else if (c == '$') {
				if (tokenType != TokenType.VARIABLE)
					tokenTypeNext = TokenType.VARIABLE;
			} else if (c == ':') {
				if (tokenType != TokenType.LABEL)
					tokenTypeNext = TokenType.LABEL;
			} else if (tokenType != TokenType.REFERENCE && (c > 47 && c < 58 /* 0-9 */ || (c == '.' && tokenType == TokenType.NUMBER))) {
				if (tokenType != TokenType.NUMBER)
					tokenTypeNext = TokenType.NUMBER;
			} else if (c == '.' || c == '_' || (c > 96 && c < 123) /* a-z */ || (c > 64 && c < 91) /* A-Z */ || (c > 47 && c < 58 /* 0-9 */)) {
				if (tokenType != TokenType.REFERENCE)
					tokenTypeNext = TokenType.REFERENCE;
			} else if (c == ';') {
				if (tokenType != TokenType.STATEMENTEND)
					tokenTypeNext = TokenType.STATEMENTEND;
			} else {
				return error ("Unexpected character: " + String.valueOf (Character.toChars (kabapScript.codePointAt (i))));
			}

			if (DEBUG)
				logV (LOG_TAG, "	  END tokenType=" + tokenType + "	tokenTypeNext=" + tokenTypeNext);

			// Process the next token only if it is not an ignored type
			if (tokenTypeNext != TokenType.NULL) {
				if (DEBUG)
					logV (LOG_TAG, "	Switching to tokenTypeNext=" + tokenTypeNext);

				if (tokenType == TokenType.REFERENCE && Arrays.asList (tokenFlow).contains (tokenValue.toLowerCase ())) {
					tokenType = TokenType.FLOW;
					tokenValue = tokenValue.toLowerCase ();

					if (tokenValue.equals ("if"))
						tokenFlowConditional = true;
				}

				// If it is a known token process it with basic checks and balances
				if (tokenType != TokenType.NULL) {
					if (tokenCountStatement > 0 && tokenType == TokenType.LABEL) {
						// Ensure a label is its own statement
						return error ("A label must be in its own statement");
					} else if (tokenCountStatement > 0 && (statementTokens.get (tokenCountStatement - 1).type == TokenType.VARIABLE && statementTokens.get (tokenCountStatement - 1).value.equals ("$") || statementTokens.get (tokenCountStatement - 1).type == TokenType.LABEL && statementTokens.get (tokenCountStatement - 1).value.equals (":"))) {
						if (tokenType != TokenType.REFERENCE) {
							return error ("Required " + statementTokens.get (tokenCountStatement - 1).type.toString ().toLowerCase () + " after " + statementTokens.get (tokenCountStatement - 1).value);
						} else {
							tokenValue = tokenValue.toLowerCase ();

							// Ensure reference/label/variable names are sensible
							if (!tokenValue.matches ("[a-z_]+[a-z0-9_]*"))
								return error ("Invalid " + statementTokens.get (tokenCountStatement - 1).type.toString ().toLowerCase () + ", must start with a letter or underscore, and contain only letters, numbers and underscores");

							statementTokens.get (tokenCountStatement - 1).value = tokenValue;

							// Ensure labels are not already defined
							if (statementTokens.get (tokenCountStatement - 1).type == TokenType.LABEL) {
								if (kabapLabels.containsKey (tokenValue)) {
									for (int k = kabapLabels.get (tokenValue) - 1; k > -1; --k) {
										statementTokens = kabapTokens.get (k);
										if (statementTokens.get (0).type == TokenType.LINEHINT)
											break;
									}

									return error ("Label already used on line " + (statementTokens.get (0).type == TokenType.LINEHINT ? statementTokens.get (0).value : "unknown") + ": " + tokenValue);
								}

								kabapLabels.put (tokenValue, tokenCountKabap);
							}
						}
					} else if (tokenType == TokenType.OPERATOR && !Arrays.asList (operatorComparator).contains (tokenValue) && !Arrays.asList (operatorMathematical).contains (tokenValue) && !Arrays.asList (operatorAssignment).contains (tokenValue) && !Arrays.asList (operatorString).contains (tokenValue)) {
						// Ensure operators are known
						return error ("Unknown operator: " + tokenValue);
					} else if (tokenType == TokenType.STATEMENTEND && tokenCountStatement == 0 && tokenTypePrevious != TokenType.COMMENT) {
						// Ensure there is an actual statement preceeding a STATEMENTEND
						return error ("Missing statement");
					} else if (tokenFlowConditional && (tokenType == TokenType.LABEL || tokenType == TokenType.BLOCKEND)) {
						// Ensure flow conditional has a succeeding condition
						return error ("A conditional cannot be followed by a " + tokenType.toString ().toLowerCase ());
					} else if (tokenType != TokenType.STATEMENTEND) {
						if (DEBUG)
							logV (LOG_TAG, "	Adding entry {" + tokenType + ", " + tokenValue + "}");

						// Write the token to the statement
						tokenEntry = new TokenEntry ();
						tokenEntry.type = tokenType;
						tokenEntry.value = tokenValue;
						statementTokens.add (tokenEntry);
						++tokenCountStatement;
					}

					tokenValue = "";

					// Assess if the statement is a candidate to write to the Kabap program
					if (tokenCountStatement > 0 && (tokenType == TokenType.STATEMENTEND || tokenType == TokenType.LINEHINT || tokenType == TokenType.BLOCKSTART || tokenType == TokenType.BLOCKEND)) {

						if (tokenFlowConditional && statementTokens.get (0).type != TokenType.LINEHINT && (statementTokens.get (0).type != TokenType.FLOW || !statementTokens.get (0).value.equals ("if")))
							tokenFlowConditional = false;

						// Append the statement if necessary
						if (tokenEntry.type == TokenType.LINEHINT && tokenCountKabap > 0 && ((TokenEntry) (kabapTokens.get (tokenCountKabap - 1).get (0))).type == TokenType.LINEHINT) { // Do not add pointless consecutive line hints, update the last hint
							((TokenEntry) (kabapTokens.get (tokenCountKabap - 1).get (0))).value = tokenEntry.value;
						} else if (i < j || tokenEntry.type != TokenType.LINEHINT) { // Append if not a trailing line hint
							kabapTokens.add (statementTokens);
							++tokenCountKabap;
						}

						// Reset working storage for the next statement tokens
						statementTokens = new ArrayList<TokenEntry> ();
						tokenCountStatement = 0;
					}
				}

				// Munge whitespace to an ignored token type and record the previous/next token types
				tokenType = (tokenTypeNext != TokenType.WHITESPACE ? tokenTypeNext : TokenType.NULL);
				tokenTypePrevious = tokenType;
				tokenTypeNext = TokenType.NULL;
			}

			// Append to the current token value or update the current line hint as necessary
			if (tokenType != TokenType.NULL && tokenType != TokenType.BLOCKSTART && tokenType != TokenType.BLOCKEND && c != 0) {
				tokenValue += c;
			} else if (tokenType == TokenType.LINEHINT) {
				tokenValue = String.valueOf (++lineNumber);
			}
		}

		// Unbalanced nested blocks are bad
		if (tokenBlockNests > 0)
			return error ("Unclosed open block");

		// A flow conditional missing a condition is bad
		if (tokenFlowConditional)
			return error ("A conditional requires a statement after");

		if (DEBUG)
			debugDumpTokens ();

		kabapScript = null; // The tokeniser will now be skipped if called again on this script
		return true;
	}

	/**
	 * Step 2: Optimisation
	 * <p>Attempt to reduce complexity (and token count) by pre-computing, merging, shortening and discarding things.
	 * Be warned that this process is not finalised yet and the optimisation levels may change in future releases,
	 * see the change log! Running with optimiseLevel = 3 is equivalent to full minification.</p>
	 * <pre>
	 * 0 = None
	 * 1 = TODO Merge string/numeric literal concatenation operators ("a"<<"b"<<1 -> "ab1")
	 *   = TODO Pre-compute numeric literal mathematical operators (3 * 4 -> 12)
	 *   = TODO Remove conditional statements when always false/checks when always true
	 * 2 = Discard line hints
	 * 3 = Shorten/obfuscate variable and label names (full minification)
	 * </pre>
	 *
	 * @param optimiseLevel The level of optimisation (0 = none, 3 = all)
	 * @return True if optimisation level is valid, false otherwise
	 */
	private boolean optimise (int optimiseLevel) {
		if (DEBUG)
			logV (LOG_TAG, "Optimising tokens to level: " + optimiseLevel);

		// Check level is valid
		if (optimiseLevel < 0 || optimiseLevel > 3)
			return error ("Optimisation level is out of bounds");

		// Check there is something to do
		if (optimiseLevel == 0)
			return true;

		// Working storage
		String strTmp = "";
		HashMap<String, String> minifiedNames = new HashMap<String, String> ();

		int j; // Stupid Java
		for (int i = 0; i < 2; ++i) {
			// Multiplex the loop direction when processing tokens (i=0=backward, i=1=forward)
			for (int k = (i == 0 ? kabapTokens.size () - 1 : 0); (i == 0 ? k > -1 : k < kabapTokens.size ()); j = (i == 0 ? --k : ++k)) {
				for (int m = kabapTokens.get (k).size () - 1; m > -1; --m) {
					tokenEntry = (TokenEntry) kabapTokens.get (k).get (m);

					// Get rid of all line hints
					if (i == 0 && optimiseLevel >= 2 && tokenEntry.type == TokenType.LINEHINT)
						kabapTokens.get (k).remove (m);

					// Minify/obfuscate variable and label names
					if (i == 1 && optimiseLevel >= 3 && (tokenEntry.type == TokenType.VARIABLE || tokenEntry.type == TokenType.LABEL || (m > 0 && ((TokenEntry) kabapTokens.get (k).get (m - 1)).type == TokenType.FLOW && ((TokenEntry) kabapTokens.get (k).get (m - 1)).value.equals ("goto") && tokenEntry.type == TokenType.REFERENCE))) {
						strTmp = (tokenEntry.type == TokenType.VARIABLE ? "$" : (tokenEntry.type == TokenType.LABEL || tokenEntry.type == TokenType.REFERENCE ? ":" : "")) + tokenEntry.value;
						// If this is the first encounter of the name to be minified store it in a lookup for later
						if (!minifiedNames.containsKey (strTmp))
							minifiedNames.put (strTmp, index2Label (minifiedNames.size ()));

						tokenEntry.value = minifiedNames.get (strTmp);
					}
				}

				// If the statement is now empty, remove it
				if (kabapTokens.get (k).size () == 0) {
					kabapTokens.remove (k);

					// Bump down any label pointers as the indices have now shrunk
					for (Map.Entry<String, Integer> kabapLabel : kabapLabels.entrySet ()) {
						if (kabapLabel.getValue () >= k)
							kabapLabel.setValue (kabapLabel.getValue () - 1);
					}
				}
			}
		}

		// Update label pointer names from the lookup created earlier
		if (optimiseLevel >= 3) {
			HashMap<String, Integer> kabapLabelsNew = new HashMap<String, Integer> ();

			// Make sure only to populate labels (:) when there was an existing label
			for (Map.Entry<String, String> minifiedName : minifiedNames.entrySet ()) {
				strTmp = minifiedName.getKey ();
				if (strTmp.startsWith (":")) {
					strTmp = strTmp.substring (1);
					if (kabapLabels.containsKey (strTmp))
						kabapLabelsNew.put (minifiedName.getValue (), kabapLabels.get (strTmp));
				}
			}

			kabapLabels = kabapLabelsNew;
		}

		return true;
	}

	/**
	 * Step 3: Resolution/Execution
	 * <p>Uses JIT evaluation on a single outer loop (int i) over all the tokenised statements (consider this
	 * effectively the Program Counter) and 5-pass child inner loop (int k) responsible for resolving different
	 * operations, which may optionally do any preliminary work related to those types of operations. This inner
	 * loop also determines the direction of the next child loop, the token resolution loop (int m) which is
	 * bi-directional and operates on the tokenised entries in those statements to resolve, reduce and perform
	 * the execution proper.</p>
	 * <p>The child token resolution loop direction is referred to below as <b>F</b> Forward / <b>B</b> Backward</p>
	 * <pre>
	 * Outer Loop: First to last statement (but goto jumps in any direction to label index)
	 *   Inner Loop 0/F: Flow control on break|goto. Check assignments. Replace references.
	 *   Inner Loop 1/B: Mathematical operator resolution.
	 *   Inner Loop 2/B: String operator resolution.
	 *   Inner Loop 3/B: Comparator operation resolution.
	 *   Inner Loop 4/F: Flow control on conditional if. Write Lvalue if assignment.
	 *     Token Resolution Loop: Iterate over statement tokens to do the actual execution
	 * </pre>
	 */
	private boolean execute () {
		if (DEBUG)
			logV (LOG_TAG, "Executing Kabap program");

		// Working storage
		int watchdogTickCounter = 0;
		boolean statementAssignment = false;
		String strTmp = "";

		// Check if the tokeniser or optimiser had a problem
		if (kabapScript != null)
			return false;

		// Prepare
		lineNumber = 0;
		stdout = stderr = "";

		// Check there is something to actually execute
		if (kabapTokens == null)
			return error ("Script or tokens must be loaded before running");

		// The outer loop
		for (int i = 0, j = kabapTokens.size (); i < j; ++i) {
			// Make sure the script does not run indefinitely (i.e., infinite goto loop)
			if (++watchdogTickCounter == watchdogTickLimit && watchdogTickLimit > 0)
				return error ("Watchdog " + watchdogTickCounter + " ticks timeout, execution break");

			// Duplicate each statement to working storage as it is processed (original kabapTokens is never altered)
			statementTokens = new ArrayList<TokenEntry> ();
			for (int k = 0, l = kabapTokens.get (i).size (); k < l; ++k) {
				// Deep copy the objects to break references
				tokenEntry = new TokenEntry ();
				tokenEntry.type = ((TokenEntry) kabapTokens.get (i).get (k)).type;
				tokenEntry.value = ((TokenEntry) kabapTokens.get (i).get (k)).value;
				statementTokens.add (tokenEntry);
			}

			// Reset the assignment decision to an assumption of no
			statementAssignment = false;

			// The inner loop
			for (int k = 0; k < 5; ++k) {
				// Reset statement count because it will change between loops
				int n = statementTokens.size ();

				if (DEBUG) {
					if (k == 0)
						logV (LOG_TAG, "----------------------------------------------------");

					logV (LOG_TAG, ">>>>>>>>>>>>>>>>>>>> Iteration " + k + " (" + (k % 4 == 0 ? "forward" : "backward") + ")");

					for (int m = 0; m < n; ++m)
						logV (LOG_TAG, "	" + m + "	" + statementTokens.get (m).type + "	" + statementTokens.get (m).value);
				}

				// Inner Loop 0, forward
				if (k == 0) {
					// Quickly move to the next statement if line hint or label
					if (n == 1 && statementTokens.get (0).type == TokenType.LINEHINT) {
						// Set internal line number hint
						lineNumber = Integer.valueOf (statementTokens.get (0).value);
						break;
					} else if (n == 1 && statementTokens.get (0).type == TokenType.LABEL) {
						break;
					}

					// Flow control on break|goto
					if (statementTokens.get (0).type == TokenType.FLOW) {
						if (statementTokens.get (0).value.equals ("break")) {
							if (n > 1)
								return error ("Nothing can be after break");

							return true;
						}

						if (statementTokens.get (0).value.equals ("goto")) {
							if (n < 2 || statementTokens.get (1).type != TokenType.REFERENCE)
								return error ("Expected label after goto");

							if (n > 2)
								return error ("Nothing can be after label");

							strTmp = statementTokens.get (1).value.toLowerCase ();

							if (!kabapLabels.containsKey (strTmp))
								return error ("Unknown label: " + statementTokens.get (1).value);

							i = kabapLabels.get (strTmp);
							break;
						}
					}

					// Decide if this statement is an assignment
					statementAssignment = false;
					int m, o;
					for (m = 0, o = -1; m < n; ++m) {
						if (statementTokens.get (m).type == TokenType.OPERATOR && Arrays.asList (operatorAssignment).contains (statementTokens.get (m).value)) {
							if (o == -1) {
								o = m;
								statementAssignment = true;
							} else {
								return error ("Only 1 assignment can be in a statement");
							}
						}
					}

					if (statementAssignment) {
						if (o != 1)
							return error ("Assignment expects 1 left-hand value");

						if (o == n || n < 3)
							return error ("Assignment expects a right-hand value");

						if (statementAssignment && statementTokens.get (0).type != TokenType.VARIABLE && statementTokens.get (0).type != TokenType.REFERENCE)
							return error ("Assignment left-hand value must be a variable or reference");
					}
				}

				// Inner Loop 4, forward
				if (k == 4) {
					// If this is a write operation
					if (statementAssignment) {
						if (n > 3)
							return error ("Assignment takes only one right-hand value");

						if (statementTokens.get (0).type == TokenType.VARIABLE) {
							kabapVariables.put (statementTokens.get (0).value.toLowerCase (), statementTokens.get (2).value);
						} else if (statementTokens.get (0).type == TokenType.REFERENCE) {
							if (statementTokens.get (0).value.toLowerCase ().equals ("return")) {
								stdout = statementTokens.get (2).value;
							} else {
								strTmp = extensionCall (ReferenceMessageType.WRITE, tokenEntry.value, statementTokens.get (2).value);

								// Kabap does not have null, so this indicates error() was just called, so exit with false
								if (strTmp == null)
									return false;
							}
						}
					}

					// Flow control on conditional if
					if (statementTokens.get (0).type == TokenType.FLOW && statementTokens.get (0).value.equals ("if")) {
						if (n < 2) {
							return error ("Missing if condition to be evaluated");
						} else if (n > 2) {
							return error ("Only 1 if condition can be evaluated");
						} else if (statementTokens.get (1).type == TokenType.FLOW || statementTokens.get (1).type == TokenType.BLOCKSTART || statementTokens.get (1).type == TokenType.BLOCKEND) {
							return error ("An if condition cannot contain a " + statementTokens.get (1).type.toString ().toLowerCase ());
						}

						if (numberExtract (statementTokens.get (1).value, 0) == 0f) {
							tokenBlockNests = 0;
							int m = i + 1;
							for (; m < j; ++m) {
								tokenEntry = (TokenEntry) kabapTokens.get (m).get (0);
								if (tokenEntry.type == TokenType.LINEHINT || (tokenBlockNests == 0 && tokenEntry.type == TokenType.FLOW && tokenEntry.value.equals ("if"))) {
									continue;
								} else if (tokenEntry.type == TokenType.BLOCKSTART) {
									++tokenBlockNests;
								} else if (tokenEntry.type == TokenType.BLOCKEND) {
									--tokenBlockNests;
								}

								if (tokenBlockNests == 0)
									break;
							}

							// TODO Seems correct to check these conditions but never able to actually produce them. Need a unit test which can. You win a prize if you can make one!
							if (m == j || tokenBlockNests > 0)
								return error ("Could not find the end of a conditional block");

							i = m;
						}
					}

					break;
				}

				// Token resolution loop
				int o; // Stupid Java
				// Multiplex the Inner Loop direction when processing tokens
				for (int m = (k % 4 == 0 ? 0 : n - 1); (k % 4 == 0 ? m < n : m > -1); o = (k % 4 == 0 ? ++m : --m)) {
					tokenEntry = statementTokens.get (m);

					// Inner Loop 0 for reference substitution
					if (k == 0) {
						// If an assignment some additional checks are required
						if (statementAssignment) {
							// Skip resolving Lvalue and assignment operator (=)
							if (m < 2)
								continue;

							// Check assignment Rvalue components are valid token types which can be read/used
							if (tokenEntry.type != TokenType.OPERATOR && tokenEntry.type != TokenType.VARIABLE && tokenEntry.type != TokenType.STRING && tokenEntry.type != TokenType.NUMBER && tokenEntry.type != TokenType.REFERENCE) {
								return error ("Assignment cannot contain a " + tokenEntry.type.toString ().toLowerCase ());
							}
						}

						// The token is something which can be replaced with a variable lookup or external reference call
						if (tokenEntry.type == TokenType.VARIABLE) {
							// All variables must be pre-defined before use, early Kabap returned an empty string
							strTmp = tokenEntry.value.toLowerCase ();
							if (!kabapVariables.containsKey (strTmp))
								return error ("Undefined variable: " + tokenEntry.value);

							tokenEntry.type = TokenType.STRING;
							tokenEntry.value = kabapVariables.get (strTmp);
						} else if (tokenEntry.type == TokenType.REFERENCE) {
							if (tokenEntry.value.toLowerCase ().equals ("return")) {
								return error ("Cannot " + (statementAssignment ? "read" : "call") + " from a return");
							} else {
								strTmp = extensionCall (ReferenceMessageType.READ, tokenEntry.value);

								// Kabap does not have null, so this indicates error() was just called, so exit with false
								if (strTmp == null)
									return false;

								tokenEntry.type = TokenType.STRING;
								tokenEntry.value = strTmp;
							}
						}
					}

					// Inner Loop resolution: 1 for mathematical operator, 2 for string operator, 3 for comparator operator
					if (tokenEntry.type == TokenType.OPERATOR && ((k == 1 && Arrays.asList (operatorMathematical).contains (tokenEntry.value)) || (k == 2 && Arrays.asList (operatorString).contains (tokenEntry.value)) || (k == 3 && Arrays.asList (operatorComparator).contains (tokenEntry.value)))) {
						// Check there is a left operand
						if (m == (statementAssignment ? 2 : 0))
							return error ("Missing left-hand operand before operator: " + tokenEntry.value);

						// Check there is a right operand (increment and decrement operators do not require one)
						if (m + 1 == n && !tokenEntry.value.equals ("++") && !tokenEntry.value.equals ("--"))
							return error ("Missing right-hand operand after operator: " + tokenEntry.value);

						// Check the left operand is a string or number
						if (statementTokens.get (m - 1).type != TokenType.STRING && statementTokens.get (m - 1).type != TokenType.NUMBER)
							return error ("Left-hand operand cannot be a " + statementTokens.get (m - 1).type.toString ().toLowerCase ());

						// Check if any right operand is a string or number
						if (!tokenEntry.value.equals ("++") && !tokenEntry.value.equals ("--") && statementTokens.get (m + 1).type != TokenType.STRING && statementTokens.get (m + 1).type != TokenType.NUMBER)
							return error ("Right-hand operand cannot be a " + statementTokens.get (m + 1).type.toString ().toLowerCase ());

						// See if this is a string, mathematical or comparator operator
						if (k == 2) { // String
							if (tokenEntry.value.equals ("<<")) {
								statementTokens.get (m - 1).type = TokenType.STRING;
								statementTokens.get (m - 1).value = statementTokens.get (m - 1).value + statementTokens.get (m + 1).value;
								statementTokens.remove (m + 1);
							}
						} else if (k == 1 || k == 3) { // Mathematical or Comparator
							// Munge the operands to numerical values
							double tokenValueL = 0, tokenValueR = 0;

							if (!tokenEntry.value.equals ("==") && !tokenEntry.value.equals ("!="))
								tokenValueL = numberExtract (statementTokens.get (m - 1).value, 0);

							if (k == 1 && (tokenEntry.value.equals ("++") || tokenEntry.value.equals ("--"))) {
								tokenValueR = (tokenEntry.value.equals ("++") ? 1 : tokenEntry.value.equals ("--") ? -1 : 0);
							} else if (!tokenEntry.value.equals ("==") && !tokenEntry.value.equals ("!=")) {
								tokenValueR = numberExtract (statementTokens.get (m + 1).value, 0);
							}

							if (tokenEntry.value.equals ("==")) {
								strTmp = (statementTokens.get (m - 1).value.equalsIgnoreCase (statementTokens.get (m + 1).value) ? "1" : "0");
							} else if (tokenEntry.value.equals ("!=")) {
								strTmp = (statementTokens.get (m - 1).value.equalsIgnoreCase (statementTokens.get (m + 1).value) ? "0" : "1");
							} else if (tokenEntry.value.equals ("+") || tokenEntry.value.equals ("++") || tokenEntry.value.equals ("--")) {
								strTmp = numberFormat (tokenValueL + tokenValueR);
							} else if (tokenEntry.value.equals ("-")) {
								strTmp = numberFormat (tokenValueL - tokenValueR);
							} else if (tokenEntry.value.equals ("*")) {
								strTmp = numberFormat (tokenValueL * tokenValueR);
							} else if (tokenEntry.value.equals ("/")) {
								if (tokenValueR == 0) {
									strTmp = "0"; // Most Kabap users would not understand a division by zero error
								} else {
									strTmp = numberFormat (tokenValueL / tokenValueR);
								}
							} else if (tokenEntry.value.equals ("%")) {
								strTmp = numberFormat (tokenValueL % tokenValueR);
							} else if (tokenEntry.value.equals ("^")) {
								strTmp = numberFormat (Math.pow (tokenValueL, tokenValueR));
							} else if (tokenEntry.value.equals ("<")) {
								strTmp = (tokenValueL < tokenValueR ? "1" : "0");
							} else if (tokenEntry.value.equals ("<=")) {
								strTmp = (tokenValueL <= tokenValueR ? "1" : "0");
							} else if (tokenEntry.value.equals (">=")) {
								strTmp = (tokenValueL >= tokenValueR ? "1" : "0");
							} else if (tokenEntry.value.equals (">")) {
								strTmp = (tokenValueL > tokenValueR ? "1" : "0");
							}

							statementTokens.get (m - 1).type = TokenType.NUMBER;
							statementTokens.get (m - 1).value = strTmp;
							if (!tokenEntry.value.equals ("++") && !tokenEntry.value.equals ("--"))
								statementTokens.remove (m + 1);
						}

						statementTokens.remove (m);
						tokenEntry = null;
						m--;
					}

					// Update this token in the statement now it has been processed
					if (tokenEntry != null)
						statementTokens.set (m, tokenEntry);
				}
			}
		}

		return true;
	}

	/**
	 * Sends an error to stderr with line number (if available)
	 *
	 * @param message Optional string or null of the message to display
	 * @return False (used as a placeholder for returns in other methods)
	 */
	private boolean error (String message) {
		stderr = (lineNumber > 0 ? "Line " + lineNumber + ": " : "") + (message != null ? message : "Unknown error");
		return false;
	}

	/**
	 * Calls a Kabap extension
	 *
	 * @param type READ/WRITE message type
	 * @param name External reference name
	 * @return Response string, or null if an error occurred, and stderr will contain the error message
	 */
	private String extensionCall (ReferenceMessageType type, String name) {
		return extensionCall (type, name, null);
	}

	/**
	 * Calls a Kabap extension
	 *
	 * @param type READ/WRITE message type
	 * @param name External reference name
	 * @param value String for the extension to process
	 * @return Response string, or null if an error occurred, and stderr will contain the error message
	 */
	private String extensionCall (ReferenceMessageType type, String name, String value) {
		if (DEBUG)
			logV (LOG_TAG, "Calling extensions for " + type.toString ().toLowerCase () + " with reference: " + name);

		String[] nameParts = name.split ("\\.");
		nameParts[0] = nameParts[0].toLowerCase ();

		ArrayList<Extension> extensions = (kabapExtensions.containsKey (nameParts[0]) ? kabapExtensions.get (nameParts[0]) : (kabapExtensions.containsKey ("*") ? kabapExtensions.get ("*") : null));

		if (extensions != null) {
			// Construct the message to share
			ReferenceMessage message = new ReferenceMessage (type, name, value);

			// Loop through all extensions sharing the message
			for (int i = 0, j = extensions.size (); i < j; ++i) {
				message = extensions.get (i).referenceHandler (message);

				if (DEBUG)
					logV (LOG_TAG, "[" + (i + 1) + "/" + j + "] " + "result=" + (message.result == null ? "NULL" : message.result.toString ()) + ", value=" + message.value);

				if (message.result == ReferenceMessageResult.IGNORED) { // This extension skipped it, post to the next
					continue;
				} else if (message.result == ReferenceMessageResult.HANDLED_OKAY) { // The value contains any result
					return message.value;
				} else if (message.result == ReferenceMessageResult.HANDLED_FAIL) { // The value contains any error message
					error (!message.value.isEmpty () ? message.value : "Extension is broken (no error message given)");
					return null;
				} else {
					error ("Extension is broken (invalid result value)");
					return null;
				}
			}
		}

		if (DEBUG)
			logD (LOG_TAG, "Reference not found");

		error ("Reference not found: " + name);
		return null;
	}

	/**
	 * Formats floating point numbers to chosen decimal places (with rounding)
	 * <p>See {@link #scaleSet(int)} for scale and rounding strategy</p>
	 *
	 * @param number Floating point number to format
	 * @return String of formatted number
	 */
	private String numberFormat (double number) {
		return decimalFormat.format (number);
	}

	/**
	 * Dumps the current parsed/loaded token structure with labels to the verbose log
	 */
	private void debugDumpTokens () {
		if (DEBUG) {
			logV (LOG_TAG, "**************************************************************");

			logV (LOG_TAG, "Tokenisation:");
			for (int i = 0, j = kabapTokens.size (); i < j; ++i) {
				logV (LOG_TAG, "" + i);
				statementTokens = kabapTokens.get (i);
				for (int k = 0, l = statementTokens.size (); k < l; ++k)
					logV (LOG_TAG, "	" + k + "	" + statementTokens.get (k).type + "	" + statementTokens.get (k).value);
			}
			logV (LOG_TAG, "");

			logV (LOG_TAG, "Labels:");
			logV (LOG_TAG, "" + kabapLabels);

			logV (LOG_TAG, "**************************************************************");
		}
	}

	/**
	 * Converts an index to a spreadsheet-column-esque unique label; a..z, aa..zz, aaa..zzz, etc
	 *
	 * @param index Index of label within the sequence
	 * @return The label corresponding to the index
	 */
	private static String index2Label (int index) {
		return index < 0 ? "" : index2Label ((int) (index / 26) - 1) + (char) (97 + (index % 26));
	}

	/**
	 * Attempts to extract a number from a string
	 *
	 * @param number The string containing a number to extract
	 * @param numberDefault The fallback number to return if extraction failed
	 * @return The extracted number or the numberDefault fallback on failure
	 */
	private static double numberExtract (String number, double numberDefault) {
		try {
			return Double.valueOf (number);
		} catch (NumberFormatException e) {
			return numberDefault;
		}
	}

	/**
	 * Implementation specific logging for Verbose messages
	 *
	 * @param msg The message to log
	 */
	protected static void logV (String tag, String msg) {
		System.out.println ("V: " + (tag.isEmpty () ? "" : tag + ": ") + msg);
	}

	/**
	 * Implementation specific logging for Debug messages
	 *
	 * @param msg The message to log
	 */
	protected static void logD (String tag, String msg) {
		System.out.println ("D: " + (tag.isEmpty () ? "" : tag + ": ") + msg);
	}
}
