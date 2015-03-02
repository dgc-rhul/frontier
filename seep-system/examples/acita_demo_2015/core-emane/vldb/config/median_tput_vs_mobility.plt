set terminal pdf 
set output sprintf("%s/%s/median_tput_vs_mobility.pdf",outputdir,timestr)

set title "Median throughput vs mobility for different replication factors (k)"
set xlabel "Random waypoint pause time (s) or speed (m/s)"
set ylabel "Median throughput (Kb/s)"
#set yrange [0:100]

set border linewidth 1.5
set style line 1 linewidth 2.5 linecolor rgb "red"
set style line 2 linewidth 2.5 linecolor rgb "blue"
set style line 3 linewidth 2.5 linecolor rgb "green"
set style line 4 linewidth 2.5 linecolor rgb "pink"

plot sprintf("%s/%s/1k.data",outputdir,timestr) using 1:7 title "k=1" w lines linestyle 1, \
	sprintf("%s/%s/2k.data",outputdir,timestr) using 1:7 title "k=2" w lines linestyle 2, \
	sprintf("%s/%s/3k.data",outputdir,timestr) using 1:7 title "k=3" w lines linestyle 3, \
	sprintf("%s/%s/5k.data",outputdir,timestr) using 1:7 title "k=5" w lines linestyle 4