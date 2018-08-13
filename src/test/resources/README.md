# Kabap Test Suite
#### Platform independent unit test resources

This project includes unit tests specific to the functioning of the Kabap implementation on this platform, however they do not test the Kabap language itself.  In order to completely test the implementation on this platform you will need the Kabap Test Suite which will become part of the unit testing.  The test suite is shared across all platforms and provides a base reference of correctness for the language.

In order to obtain the Kabap Test Suite you need to clone the repository in to the same directory as this file.

```shell
cd resources
git clone https://github.com/KabapLang/KabapTestSuite.git
```

:octocat: If you do not have Git installed you can download and decompress a Zip file of the latest test suite from https://github.com/KabapLang/KabapTestSuite/archive/master.zip

After downloading you may need to refresh/sync your IDE workspace for it to see the new files or it might incorrectly report the test suite does not exist.  When the test suite is in place you can begin unit testing by launching the JUnit4 launch configuration in the project.