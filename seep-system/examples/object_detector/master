#!/bin/bash

architecture=$(arch)

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

cd tmp
java -cp "../lib/*:../lib/$architecture/*" uk.ac.imperial.lsds.seep.Main Master `pwd`/../dist/object_detector.jar com.hayderhassan.objectdetector.Base
cd ..