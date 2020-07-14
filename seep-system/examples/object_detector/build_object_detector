#!/bin/bash

architecture=$(arch)

if [[ "$architecture" != "armv7l" && "$architecture" != "x86_64" ]]; then
    echo "This system has only been tested on x86_64 and armv7l, please use a device with one of those architectures."
    exit 2
fi

project_dir=$(readlink -m ../../../..)

if [[ "$PROJECT_ROOT" != $project_dir ]] ; then
    export PROJECT_ROOT=$project_dir
    echo 'export PROJECT_ROOT='$project_dir >> ~/.bashrc
    . ~/.bashrc
fi

obj_dir=$PWD

if [[ "$LD_LIBRARY_PATH" != *"`pwd`/lib/$architecture"* ]] ; then
    export LD_LIBRARY_PATH=${obj_dir}/lib/$architecture:$LD_LIBRARY_PATH
    echo 'export LD_LIBRARY_PATH='${obj_dir}'/lib/'$architecture:$LD_LIBRARY_PATH >> ~/.bashrc
    . ~/.bashrc
fi

rm -rf $obj_dir/src/main/resources/images/detections/*.jpg

#mkdir -p ~/.m2
#cp maven_settings.xml ~/.m2/settings.xml

cd ../../..

if [[ "$architecture" == "x86_64" ]]
then
        cp $obj_dir/build.xml.core $obj_dir/build.xml
        cp seep-system/pom.xml.core seep-system/pom.xml
else
        cp $obj_dir/build.xml.pi $obj_dir/build.xml
        cp seep-system/pom.xml.pi seep-system/pom.xml
fi

pushd seep-system/examples/object_detector ; ant clean ; popd
rm -f seep-system/examples/object_detector/lib/seep*.jar

mvn install:install-file -DgroupId=soot -DartifactId=soot-framework -Dversion=2.5.0 -Dpackaging=jar -Dfile=libs/soot/soot-framework/2.5.0/soot-2.5.0.jar
mvn clean compile assembly:single

cp seep-system/target/seep-system-0.0.1-SNAPSHOT.jar seep-system/examples/object_detector/lib

mkdir -p seep-system/examples/object_detector/tmp
rm seep-system/examples/object_detector/tmp/*

pushd seep-system/examples/object_detector ; ant dist ; popd
pushd seep-system/examples/object_detector ; ant ; popd

cd $obj_dir
