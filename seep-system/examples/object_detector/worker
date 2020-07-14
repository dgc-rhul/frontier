#! /bin/bash

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

if [ "$#" -ne 1 ]; then
    echo "Please provide a port number as an argument, e.g. ./worker 3501"
else
   java -cp "../lib/*:../lib/$architecture/*" uk.ac.imperial.lsds.seep.Main Worker $1
fi

cd ..
