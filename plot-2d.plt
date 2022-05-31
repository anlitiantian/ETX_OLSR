set terminal png
set output "plot-2d.png"
set title "2-D Plot"
set xlabel "X Values"
set ylabel "Y Values"

set xrange [-6:+6]
plot "-"  title "2-D Data" with linespoints
-5 25
-4 16
-3 9
-2 4
-1 1
0 0
1 1
2 4
3 9
4 16
5 25
e
