#!/bin/bash
cd seep-system/examples/acita_demo_2015/core-emane
echo `pwd`
screen -d -m -S seep-ita


for n in {1..6}; do
	screen -S seep-ita -X screen $n
done

screen -S seep-ita -p 0 -X stuff 'cd ../../../..\n'
screen -S seep-ita -p 1 -X stuff 'cd ..\n'
