#!/bin/bash

if [ $# -eq 0 ]
then
	echo "No arguments supplied: Do you want to build for \"core\" or \"pi\" experiments?"
	exit;
fi

pushd seep-system/examples/acita_demo_2015 ; ant clean ; popd 
pushd seep-system/examples/stateless-simple-query ; ant clean ; popd
#mvn -Djavacpp.platform=linux-armhf clean compile assembly:single 

mkdir -p seep-system/examples/acita_demo_2015/lib 
mkdir -p seep-system/examples/stateless-simple-query/lib 
rm -f seep-system/examples/acita_demo_2015/lib/*.jar

if [[ "$1" = "core" ]]
then
	cp seep-system/pom.xml.core seep-system/pom.xml
else
	cp seep-system/pom.xml.pi seep-system/pom.xml
	cp seep-system/examples/acita_demo_2015/lib.arm/*.jar seep-system/examples/acita_demo_2015/lib
fi

mvn install:install-file -DgroupId=soot -DartifactId=soot-framework -Dversion=2.5.0 -Dpackaging=jar -Dfile=libs/soot/soot-framework/2.5.0/soot-2.5.0.jar
mvn clean compile assembly:single 

cp seep-system/target/seep-system-0.0.1-SNAPSHOT.jar seep-system/examples/acita_demo_2015/lib 
cp seep-system/target/seep-system-0.0.1-SNAPSHOT.jar seep-system/examples/stateless-simple-query/lib 

mkdir -p seep-system/examples/acita_demo_2015/tmp

if [[ "$1" = "core" ]]
then
	pushd seep-system/examples/acita_demo_2015 ; ant dist-res ; popd 
else
	pushd seep-system/examples/acita_demo_2015 ; ant dist ; popd 
fi

pushd seep-system/examples/stateless-simple-query ; ant ; popd
#./gradlew build -x test
