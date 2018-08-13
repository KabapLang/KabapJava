# Kabap for Java

#### An implementation of Kabap for the Java Virtual Machine

Kabap for Java is the core for all other Java based platforms that have Kabap implementations (Android, AWS Lambda, etc.).  You can use this repository in multiple ways depending on your preference:
* Download the full repo and run it (this project is a standalone command-line Kabap executor)
* Download the full repo and change it to build your own project on top of it
* Download the full repo and link it to your existing project so the classes are available to use
* Copy some `src/main/java/org/kabap/Kabap*.java` files to your existing project
* Use `kabap.jar` directly in your project or on the command line: `java -jar kabap.jar`

The *easiest* way to get started adding Kabap to your existing project just requires copying 1 file and should take less than 5 minutes.  At the very minimum you only need `src/main/java/org/kabap/Kabap.java`.

Each platform has extensions that enhance the functionality of Kabap.  Without any extensions Kabap is a sandboxed and secure computing environment where the user cannot do any damage, change the filesystem or make network requests.  Loading extensions gives the user these abilities, so think carefully before you enable them.  If you wish to make an extension available in your project you also need to copy the file(s) `src/main/java/org/kabap/Kabap<Extension>.java` to your project.


## Usage

When Kabap.java is available you can start using it with just a few lines of code.


#### A very simple example:

```java
Kabap kabap = new Kabap ();
kabap.script ("return = 2+2;");
kabap.run ();
System.out.println (kabap.stdout); // Outputs 4
```

#### A more complete and correct example:

```java
Kabap kabap = new Kabap ();

kabap.variableSet ("basket_quantity", 8);
kabap.variableSet ("basket_total", 34.99);

String customShippingCalc = "
	// If spent ¤50 or more then shipping is free
	if $basket_total >= 50; {
		$shipping = 0;
		break;
	}

	// Shipping is ¤1.49 per item
	$shipping = $basket_quantity * 1.49;

	// But capped at ¤10
	if $shipping > 10;
		$shipping = 10;
";

boolean success = kabap.script (customShippingCalc);
if (!success) {
	System.err.println ("The script is broken! " + kabap.stderr);
	System.exit (1);
}

success = kabap.run ();
if (!success) {
	System.err.println ("Script execution failed! " + kabap.stderr);
	System.exit (1);
}

String shippingCost = kabap.variableGet ("shipping");
System.out.println ("Success! Shipping cost = " + shippingCost); // Outputs 10
```


## Extensions supported

Standard extensions available:
* File
* Net

Platform specific extensions available:
* (none)


## Next steps

For more examples, information about features, helpful tips, technical support and more, please see the dedicated documentation repository at https://github.com/KabapLang/KabapDocs


## Testing

This repository contains platform specific unit tests which can be used to validate and exercise the implementation of Kabap to ensure correctness and compliance with the language definition.  There is also a Kabap Test Suite available which is a collection of platform-independent unit tests which verify the language itself.  You can download this repository from https://github.com/KabapLang/KabapTestSuite

It is recommended that you run the unit testing first of all to ensure what you have downloaded here makes sense and works as expected.

>**N.B.:** The test suite may take a few minutes to run and may not display any output when busy.


## Thank you
Thank you for choosing to use Kabap in your project.  We kindly accept contributions via a pull request.  If reporting an issue or bug please create a minimal test which exhibits the problem.


## License
Copyright 2017-18 White Label Dev Ltd, and contributors.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use these files except in compliance with the License.  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.