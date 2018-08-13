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

import static org.junit.Assert.assertEquals;
import static org.kabap.Kabap.logD;
import static org.kabap.Kabap.logV;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Unit testing for Kabap. The unit tests here are for testing the environment but not the language itself.
 * <p>
 * <b>### NOTE: IMPORTANT ###</b>
 * The "KabapTestSuite" repository needs to be downloaded from GitHub for language-level testing also.
 * To do this, go to the Kabap/src/test/resources directory and run:
 * git submodule foreach --recursive 'git pull origin master; git reset --hard'
 * This command can also be used to refresh the test suite in case something has gone wrong with it.
 * </p>
 *
 * @author WLD-PJ <wld-pj@kabap.org>
 * @version 1.0
 * @since 1.0
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UnitTesting {
	private static Kabap.Extension UnitTestExtension;
	private static JSONObject testList;

	/**
	 * Runs before testing to ensure Kabap Test Suite is loaded and sane, and defines the Kabap unit testing extension
	 */
	@BeforeClass
	public static void setUp () throws Exception {
		System.out.println ("Kabap unit testing");
		System.out.println ("Setting up the test environment…");

		// Read the notes in class comment above if the test suite is missing
		String testListStr = readResourceFile ("KabapTestSuite/test_list.json");
		if (testListStr == null) {
			System.out.println ("Could not load the Kabap Test Suite due to it being missing, please follow instructions in UnitTesting.java to get it");
			System.exit (1);
		}

		// Block-style comments are not official JSON (stupid decision) so remove them before parsing it
		Pattern blockComments = Pattern.compile ("/\\*.*?\\*/", Pattern.DOTALL);
		testListStr = blockComments.matcher (testListStr).replaceAll ("");

		// Read the notes in the class comment above if the test suite is not parsing properly
		try {
			testList = new JSONObject (testListStr);
		} catch (JSONException e) {
			e.printStackTrace ();
		}
		if (testList == null || !testList.getString ("utf8").equals ("✓") || !testList.has ("tests")) {
			System.err.println ("Could not load the Kabap Test Suite due to a JSON parsing error, please follow instructions in UnitTesting.java to refresh it");
			System.exit (1);
		}

		// Create an extension that some tests will use to check external references are working
		UnitTestExtension = new Kabap.Extension () {
			private final String LOG_TAG = this.getClass ().getSimpleName ();
			boolean DEBUG = false;

			public String referenceRegister (int version, boolean debug) {
				if (debug) {
					this.DEBUG = true;
					logV (LOG_TAG, "Instantiated");
				}

				return "test";
			}

			public void referenceReset () {
				if (DEBUG)
					logV (LOG_TAG, "Reset");
			}

			public Kabap.ReferenceMessage referenceHandler (Kabap.ReferenceMessage message) {
				if (DEBUG)
					logD (LOG_TAG, "Called for " + message.type.toString ().toLowerCase () + " with reference: " + message.name);

				if (message.name.equals ("test.duplicate")) { // Unit testing test, all answer this
					message.value += "BETA ";
					message.result = Kabap.ReferenceMessageResult.IGNORED;
				} else if (message.name.equals ("test.duplicatea")) { // Unit testing test, ignore this
					message.result = Kabap.ReferenceMessageResult.IGNORED;
				} else if (message.name.equals ("test.duplicateb")) { // Unit testing test, answer this
					message.value = getClass ().getName ();
					message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
				} else if (message.name.equals ("test.duplicatec")) { // Unit testing test, ignore this
					message.result = Kabap.ReferenceMessageResult.IGNORED;
				} else if (message.name.equals ("test.do")) { // Unit testing test, answer this
					message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
				} else {
					// Any other messages to UnitTestExtension skip to allow testing chaining of reference calls
					message.result = Kabap.ReferenceMessageResult.IGNORED;
				}

				return message;
			}
		};

		// Alles klar
		System.out.println ("…everything setup correctly, starting testing");
		System.out.println ();
	}

	/**
	 * An extension can be loaded; in this case an internal {@link KabapTestExtension}
	 */
	@Test
	public void _01_TestExtension () {
		System.out.println ("_02_TestExtension() checking extension can be loaded");

		Kabap kabap = new Kabap ();
		boolean extensionAdded = kabap.extensionAdd (new KabapTestExtension ());

		assertEquals (true, extensionAdded);

		System.out.println ("Extensions can be added");
		System.out.println ();
	}

	/**
	 * Loading the same extension twice fails
	 */
	@Test
	public void _02_DuplicateExtension () {
		Kabap kabap = new Kabap ();

		assertEquals (true, kabap.extensionAdd (new KabapTestExtension ()));
		assertEquals (false, kabap.extensionAdd (new KabapTestExtension ()));
	}

	/**
	 * Add a extension, use it, remove the extension, and run again - should result in unknown reference error
	 */
	@Test
	public void _03_ExtensionGoes () {
		Kabap kabap = new Kabap ();
		boolean success;

		// Setup script
		success = kabap.script ("return = test.a;");
		assertEquals (true, success);

		// Add test extension
		success = kabap.extensionAdd (new KabapTestExtension ());
		assertEquals (true, success);

		// Run 1
		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("Pass", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// Remove extensions
		kabap.extensionRemoveAll ();

		// Run 2
		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (false, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("Line 1: Reference not found: test.a", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	@Test
	public void _04_ExtensionRemove () {
		Kabap kabap = new Kabap ();
		boolean success;

		// Add anonymous closure extension a
		success = kabap.extensionAdd (new Kabap.Extension () {
			public String referenceRegister (int version, boolean debug) {
				return "a";
			}

			public void referenceReset () {
			}

			public Kabap.ReferenceMessage referenceHandler (Kabap.ReferenceMessage message) {
				return null;
			}
		});
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// Add anonymous closure extension b
		success = kabap.extensionAdd (new Kabap.Extension () {
			public String referenceRegister (int version, boolean debug) {
				return "b";
			}

			public void referenceReset () {
			}

			public Kabap.ReferenceMessage referenceHandler (Kabap.ReferenceMessage message) {
				return message;
			}
		});
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// Add named KabapTextExtension
		success = kabap.extensionAdd (new KabapTestExtension ());
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// Remove named KabapTestExtension
		success = kabap.extensionRemove (new KabapTestExtension ());
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// Remove anonymous closure extension b
		success = kabap.extensionRemove (new Kabap.Extension () {
			public String referenceRegister (int version, boolean debug) {
				return "b";
			}

			public void referenceReset () {
			}

			public Kabap.ReferenceMessage referenceHandler (Kabap.ReferenceMessage message) {
				return message;
			}
		});
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (false, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("Anonymous closure extensions cannot be removed", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	@Test
	public void _05_ExtensionBrokenNoResult () {
		Kabap kabap = new Kabap ();
		boolean success;

		// Add anonymous closure extension a
		success = kabap.extensionAdd (new Kabap.Extension () {
			public String referenceRegister (int version, boolean debug) {
				return "a";
			}

			public void referenceReset () {
			}

			public Kabap.ReferenceMessage referenceHandler (Kabap.ReferenceMessage message) {
				return message;
			}
		});
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// Call the extension (extension does not specify a message.result value)
		success = kabap.script ("a;");
		assertEquals (true, success);

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (false, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("Line 1: Extension is broken (invalid result value)", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	@Test
	public void _06_ExtensionBrokenNoError () {
		Kabap kabap = new Kabap ();
		boolean success;

		// Add anonymous closure extension a
		success = kabap.extensionAdd (new Kabap.Extension () {
			public String referenceRegister (int version, boolean debug) {
				return "a";
			}

			public void referenceReset () {
			}

			public Kabap.ReferenceMessage referenceHandler (Kabap.ReferenceMessage message) {
				message.result = Kabap.ReferenceMessageResult.HANDLED_FAIL;
				return message;
			}
		});
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// Call the extension (extension does not specify a message.result value)
		success = kabap.script ("a;");
		assertEquals (true, success);

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (false, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("Line 1: Extension is broken (no error message given)", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	/**
	 * Able to fully re-run, reuse, reset and recycle a single Kabap instance for reduced memory consumption
	 */
	@Test
	public void _07_RerunResetRecycle () {
		Kabap kabap = new Kabap ();
		boolean success;

		success = kabap.script (readResourceFile ("KabapTestSuite/test_scripts/a_variable.kabap"));
		assertEquals (true, success);

		// Set ref variable externally
		kabap.variableSet ("ref", "0");

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("1", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("2", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// The external ref variable has now gone
		kabap.reset ();

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (false, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("Line 1: Undefined variable: ref", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		success = kabap.script (readResourceFile ("KabapTestSuite/test_scripts/a_extension.kabap"));
		assertEquals (true, success);

		// test.ref is available
		success = kabap.extensionAdd (new KabapTestExtension ());
		assertEquals (true, success);

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("1", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("2", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// test.ref is still available
		kabap.reset ();

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (true, success);
		assertEquals ("1", kabap.stdout);
		assertEquals ("", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	/**
	 * Attempting to run a previously errored script still fails
	 */
	@Test
	public void _08_ErrorRerun () {
		Kabap kabap = new Kabap ();
		boolean success;

		success = kabap.script ("@");
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (false, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("Line 1: Unexpected character: @", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		success = kabap.run ();
		System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
		System.out.println (" stdout: " + kabap.stdout);
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (false, success);
		assertEquals ("", kabap.stdout);
		assertEquals ("Line 1: Unexpected character: @", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	/**
	 * Load a script and save it as tokens (both unminified and minified), comparing to previously exported tokens
	 */
	@Test
	public void _09_LoadScriptSaveTokens () {
		Kabap kabap = new Kabap ();
		boolean success;

		success = kabap.script (readResourceFile ("KabapTestSuite/test_scripts/9_kitchen_sink.kabap"));
		assertEquals (true, success);

		// Without minification
		assertEquals (readResourceFile ("KabapTestSuite/test_tokens/9_kitchen_sink_unminified.kat"), kabap.tokensSave (0));
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// With minification
		assertEquals (readResourceFile ("KabapTestSuite/test_tokens/9_kitchen_sink_minified.kat"), kabap.tokensSave (3));
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	/**
	 * Load a tokens format file via {@link Kabap#script(String)} to ensure it is rejected
	 */
	@Test
	public void _10_LoadTokensAsScript () {
		Kabap kabap = new Kabap ();
		boolean success;

		success = kabap.script (readResourceFile ("KabapTestSuite/test_tokens/9_kitchen_sink_unminified.kat"));
		System.out.println (" stderr: " + kabap.stderr);
		assertEquals (false, success);
		assertEquals ("Cannot load tokens as a script", kabap.stderr);
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	/**
	 * Load a script and save it as tokens (both unminified and minified), comparing to previously exported token files
	 */
	@Test
	public void _11_LoadScriptSaveTokens () {
		Kabap kabap = new Kabap ();
		boolean success;

		success = kabap.script (readResourceFile ("KabapTestSuite/test_scripts/9_kitchen_sink.kabap"));
		assertEquals (true, success);

		// Without minification
		assertEquals (readResourceFile ("KabapTestSuite/test_tokens/9_kitchen_sink_unminified.kat"), kabap.tokensSave (0));
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();

		// With minification
		assertEquals (readResourceFile ("KabapTestSuite/test_tokens/9_kitchen_sink_minified.kat"), kabap.tokensSave (3));
		System.out.println ("   Test: ✓ PASSED");
		System.out.println ();
	}

	/**
	 * Call {@link Kabap#run()} before any script or tokens are loaded
	 */
	@Test
	public void _12_NoScriptRun () {
		Kabap kabap = new Kabap ();

		kabap.run ();
		assertEquals ("Script or tokens must be loaded before running", kabap.stderr);
	}

	/**
	 * Save tokens with {@link Kabap#tokensSave(int)} before any script or tokens are loaded
	 */
	@Test
	public void _13_NoScriptSaveTokens () {
		Kabap kabap = new Kabap ();

		kabap.tokensSave (0);
		assertEquals ("No script or tokens have yet been loaded", kabap.stderr);
	}

	/**
	 * Save tokens with {@link Kabap#tokensSave(int)} with an invalid optimisation level
	 */
	@Test
	public void _14_OptimiseLevelInvalid () {
		Kabap kabap = new Kabap ();

		kabap.script ("");
		kabap.tokensSave (-1);
		assertEquals ("Optimisation level is out of bounds", kabap.stderr);
	}

	/**
	 * All test files in the resources directory are used in a test_list.json entry somewhere
	 */
	@Test
	public void _15_AllFilesUsed () throws Exception {
		System.out.println ("_01_TestAllFilesUsed() checking all files on disk are specified in test_list.json");

		// Get the list of tests
		JSONArray testListTests = testList.getJSONArray ("tests");

		// Iterate twice, once for scripts directory and once for tokens directory
		ClassLoader classLoader = getClass ().getClassLoader ();
		for (int i = 0; i < 2; ++i) {
			File[] testFiles = new File (classLoader.getResource ("KabapTestSuite/" + (i == 0 ? "test_scripts" : "test_tokens")).getPath ()).listFiles ();
			// Loop through the files in this directory
			for (File testFile : testFiles) {
				int k, l;
				System.out.println ("	Check: " + testFile.getName ());
				// See if it is found in the test list
				for (k = 0, l = testListTests.length (); k < l; ++k) {
					if (((JSONObject) testListTests.get (k)).getString ("filename").equals (testFile.getName ()))
						break;
				}

				if (k == l)
					System.out.println ("FAIL! ^^^ This file is NOT being tested in test_list.json");
				assertEquals (true, k < l);
				if (k == l)
					System.exit (1);
			}
			System.out.println ("All ." + (i == 0 ? "kabap" : "kt") + " files are being tested");
		}

		System.out.println ();
	}

	/**
	 * Every file in the test suite as listed in test_list.json entries is run
	 */
	@Test
	public void _16_KabapTestSuite () throws Exception {
		System.out.println ("_03_TestSuite() running all files specified in the Test Suite\n");

		JSONObject testEntry;
		Kabap kabap;
		boolean success;

		// Iterate over all the tests; tmp_filter can be used to quickly restrict testing to certain files for speed
		String testListTmpFilter = testList.optString ("tmp_filter");
		JSONArray testListTests = testList.getJSONArray ("tests");
		for (int i = 0, j = testListTests.length (); i < j; ++i) {
			testEntry = testListTests.getJSONObject (i);
			String testName = testEntry.getString ("filename");
			success = false;

			if (testListTmpFilter != null && !testListTmpFilter.isEmpty () && !testName.startsWith (testListTmpFilter))
				continue;

			System.out.println ("***** [" + (i + 1) + " of " + j + "] ***********************************************************************");
			System.out.println ("* " + testEntry.getString ("description"));

			kabap = new Kabap ();

			// Add all the extensions required by the test
			JSONArray testExtensions = testEntry.getJSONArray ("extensions");
			boolean testExtensionResult = false;
			String testExtensionName;
			for (int k = 0, l = testExtensions.length (); k < l; ++k) {
				testExtensionName = testExtensions.get (k).toString ();
				if (testExtensionName.equals ("UnitTestExtension")) {
						testExtensionResult = kabap.extensionAdd (UnitTestExtension);
				} else if (testExtensionName.equals ("KabapTestExtension")) {
						testExtensionResult = kabap.extensionAdd (new KabapTestExtension ());
				} else if (testExtensionName.equals ("CatchAll")) {
					testExtensionResult = kabap.extensionAdd (new Kabap.Extension () {
						public String referenceRegister (int version, boolean debug) {
							return "";
						}

						public void referenceReset () {
						}

						public Kabap.ReferenceMessage referenceHandler (Kabap.ReferenceMessage message) {
							message.value = "CATCHALL";
							message.result = Kabap.ReferenceMessageResult.HANDLED_OKAY;
							return message;
						}
					});
					break;
				} else {
					System.out.println ("Unknown extension type for this test");
					System.exit (1);
				}

				if (!testExtensionResult) {
					System.out.println ("Unable to add extension");
					System.exit (1);
				}
			}

			// Load the file from the relevant directory based on its type
			String testFile = null;
			if (testName.endsWith (".kabap")) {
				testFile = readResourceFile ("KabapTestSuite/test_scripts/" + testName);
				success = kabap.script (testFile);
			} else if (testName.endsWith (".kat")) {
				testFile = readResourceFile ("KabapTestSuite/test_tokens/" + testName);
				success = kabap.tokensLoad (testFile);
			} else {
				System.out.println ("Unknown test type specified: " + testName.substring (testName.lastIndexOf (".")));
				System.exit (1);
			}

			if (testFile == null) {
				System.out.println ("Could not read the test file!");
				System.exit (1);
			}

			if (success) {
				int testWatchdog = testEntry.getInt ("watchdog");
				if (testWatchdog > -1)
					kabap.watchdogSet (testWatchdog);

				success = kabap.run ();
			}

			System.out.println ();
			System.out.println ("   File: " + testName);
			System.out.println ("Success: " + (success ? "TRUE" : "FALSE"));
			System.out.println (" stdout: " + kabap.stdout);
			System.out.println (" stderr: " + kabap.stderr);

			assertEquals (testEntry.getBoolean ("success"), success);
			assertEquals (testEntry.getString ("stdout"), kabap.stdout);
			assertEquals (testEntry.getString ("stderr"), kabap.stderr);

			System.out.println ("   Test: ✓ PASSED");
			System.out.println ();
			System.out.println ();
		}
	}

	/**
	 * Reads a resource file from disk and returns it as a string
	 *
	 * @param fileName The path and filename to load (relative to the resources directory)
	 * @return String containing the file contents, or null if file cannot be read
	 */
	private static String readResourceFile (String fileName) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream ();

		try {
			InputStream inputStream = Thread.currentThread ().getContextClassLoader ().getResourceAsStream (fileName);
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
	 * Writes a string to a temporary file on disk
	 *
	 * @param fileData The string to be written
	 * @return The created file or null on error
	 */
	@SuppressWarnings("unused")
	private static File writeTmpFile (String fileData) {
		try {
			File fileHandle = File.createTempFile ("kabap", ".tmp");

			OutputStream outputStream = new FileOutputStream (fileHandle);
			outputStream.write (fileData.getBytes (StandardCharsets.UTF_8));
			outputStream.flush ();
			outputStream.close ();

			return fileHandle;
		} catch (IOException e) {
			return null;
		}
	}
}
