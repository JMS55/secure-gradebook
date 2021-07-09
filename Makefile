all: setup gradebookadd gradebookdisplay

JFLAGS = -g
JC = javac

CPFLAGS = -cp .:./bytes-1.5.0.jar:./jcommander-1.81.jar:./sqlite-jdbc-3.34.0.jar

gradebookadd:
	$(JC) $(JFLAGS) $(CPFLAGS) GradebookAdd.java Gradebook.java CLIUtils.java
	echo -e "#!/bin/bash\njava $(CPFLAGS) GradebookAdd \$$@" > gradebookadd
	chmod +x gradebookadd

gradebookdisplay:
	$(JC) $(JFLAGS) $(CPFLAGS) GradebookDisplay.java Gradebook.java CLIUtils.java
	echo -e "#!/bin/bash\njava $(CPFLAGS) GradebookDisplay \$$@" > gradebookdisplay
	chmod +x gradebookdisplay

setup:
	$(JC) $(JFLAGS) $(CPFLAGS) GradebookSetup.java Gradebook.java CLIUtils.java
	echo -e "#!/bin/bash\njava $(CPFLAGS) GradebookSetup \$$@" > setup
	chmod +x setup

clean:
	rm -f *.class
	rm -rf gradebookadd gradebookdisplay setup
