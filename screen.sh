#!/bin/bash
cd seep-system/examples/acita_demo_2015/core-emane
echo `pwd`
screen -d -m -S frontier


for n in {1..6}; do
	screen -S frontier -X screen $n
done

screen -S frontier -p 0 -X stuff 'cd ../../../..\n'
screen -S frontier -p 1 -X stuff 'cd ..\n'
