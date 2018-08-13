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

package org.kabap.example;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.kabap.Kabap;

/**
 * Kabap for Java
 *
 * @author WLD-PJ <wld-pj@kabap.org>
 * @version 1.0
 * @since 1.0
 */
public class Main {
	/**
	 * @param args The command line options when invoked
	 */
	public static void main (String[] args) {
		/** The return code sent to the invoking shell */
		int returnCode = -1;

		/** The Kabap script to run (loaded from disk or internal example) */
		String sourceData = null;

		// Process command line flags
		List <String> params = Arrays.asList (args);
		boolean paramVersion = params.contains ("--v");
		boolean paramHelp = params.contains ("--help");
		boolean paramExample = params.contains ("--hello");

		if (args.length != 1 || paramVersion || paramHelp) { // Called with wrong arguments, version or help
			// Show version
			System.out.println ("Kabap for Java (v " + Kabap.VERSION_MAJOR + "." + Kabap.VERSION_MINOR + ")\n");

			// Show usage information
			if (paramHelp || args.length != 1) {
				System.out.println ("Usage:  java -jar kabap.jar sourcefile");
				System.out.println ("        (to execute a Kabap script)");
				System.out.println ("Alternatively set run configuration arguments in your IDE");
				System.out.println ("");
				System.out.println ("Options:");
				System.out.println ("    --help     Show this help");
				System.out.println ("       --v     Show version information");
				System.out.println ("   --hello     Run internal example script");
			}

			returnCode = 2;
		} else if (paramExample) { // Called to execute the internal example Kabap script
			/* The example used:
			 * 
			 *  $answer = 2 + 2;
			 *  return = "Hello world! 2+2=" << $answer;
			 */
			sourceData = "$answer = 2 + 2;" + "\n" + "return = \"Hello world! 2+2=\" << $answer;";
		} else { // Called to execute a Kabap script from disk
			File sourceFile = new File (args[0]);
		
			// Attempt to read the file from disk
			if (!sourceFile.exists ()) {
				System.err.println ("File does not exist: " + args[0]);
				returnCode = 3;
			} else if (!sourceFile.canRead ()) {
				System.err.println ("File read permission denied: " + args[0]);
				returnCode = 13;
			} else {
				sourceData = readFile (sourceFile);
				if (sourceData == null) {
					System.err.println ("File unknown error: " + args[0]);
					returnCode = 4;
				}
			}
		}

		// If a Kabap script is in this variable, execute it
		if (sourceData != null) {
			boolean success;

			// Create a new instance
			Kabap kabap = new Kabap ();

			// Give the script to the instance
			success = kabap.script (sourceData);
			if (success) {
				// Run the script
				success = kabap.run ();

				/* The output of the script is now available in kabap.stdout and/or
				 * kabap.stderr but after the script has run, the engine is available
				 * to return things indirectly indirectly, such as the variables the
				 * script used. For example:  kabap.variableGet ("answer") == 4
				 */
			}

			// Set the return code for the shell (0=success)
			returnCode = (success ? 0 : 1);

			// Write the standard out if there is any
			if (!kabap.stdout.isEmpty ())
				System.out.println (kabap.stdout);

			// Write the standard error if there is any
			if (!kabap.stderr.isEmpty ())
				System.out.println (kabap.stderr);

			/* Returning a return code and writing data to stdout and stderr are fully
			 * optional and only done in this example so it can be invoked from a BASH
			 * shell or similar. Your application might like to keep the Kabap instance
			 * fully enclosed and separated and deal with any output and errors in its
			 * own sandbox.
			 */
		}

		System.exit (returnCode);
	}

	/**
	 * Reads a file from disk and returns it as a string
	 *
	 * @param file The file object to read
	 * @return String containing the file contents, or null if file cannot be read
	 */
	private static String readFile (File file) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream ();

		try {
			FileInputStream inputStream = new FileInputStream (file);
			byte buf[] = new byte[1024];
			int len;

			while ((len = inputStream.read (buf)) != -1)
				outputStream.write (buf, 0, len);

			outputStream.close ();
			inputStream.close ();
			
			return outputStream.toString ();
		} catch (IOException e) {
			e.printStackTrace ();
		}

		return null;
	}
}
