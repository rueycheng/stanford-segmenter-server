PACKAGE = stanford-chinese-segmenter-server
REV = 0.1

CLASSPATH = $(shell find lib -name '*.jar' | perl -e'@l = map { chomp; $$_ } <>; print join ":", @l; print "\n"')
SRC = $(shell find src -name '*.java')

JAVAC = /usr/bin/javac
JAR = /usr/bin/jar
JFLAGS = -cp $(CLASSPATH):src

BUNDLE = $(PACKAGE)-$(REV).jar

#--------------------------------------------------
# Rules
#-------------------------------------------------- 
.SUFFIXES: .java .class

.java.class:
	$(JAVAC) $(JFLAGS) $*.java

.PHONY: classes jar clean all

all: tags jar
tags:
	ctags -R src

jar: $(BUNDLE)

$(BUNDLE): $(SRC:.java=.class)
	(cd src; find -name '*.class' | xargs $(JAR) cvfM $(BUNDLE))
	mv src/$(BUNDLE) .

clean:
	find src -name '*.class' -delete
