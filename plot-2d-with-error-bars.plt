set terminal png
set output "plot-2d-with-error-bars.png"
set title "2-D Plot With Error Bars"
set xlabel "X Values"
set ylabel "Y Values"

set xrange [-6:+6]
plot "-"  title "2-D Data With Error Bars" with xyerrorbars
-5 25 0.25 2.5
-4 16 0.25 1.6
-3 9 0.25 0.9
-2 4 0.25 0.4
-1 1 0.25 0.1
0 0 0.25 0
1 1 0.25 0.1
2 4 0.25 0.4
3 9 0.25 0.9
4 16 0.25 1.6
5 25 0.25 2.5
e
