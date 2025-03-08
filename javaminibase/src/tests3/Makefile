# modify the paths appropriately
JDKPATH = /usr/lib/jvm/java-21-openjdk-amd64
LIBPATH = .:..
CLASSPATH = $(LIBPATH)
BINPATH = $(JDKPATH)/bin
JAVAC = $(JDKPATH)/bin/javac -classpath $(CLASSPATH)
JAVA  = $(JDKPATH)/bin/java  -classpath $(CLASSPATH)

# test buffer manager
LSHFIndexTest:LSHFIndexTest.java
	$(JAVAC) TestDriver.java LSHFIndexTest.java

lshfindextest: LSHFIndexTest
	$(JAVA) tests.LSHFIndexTest