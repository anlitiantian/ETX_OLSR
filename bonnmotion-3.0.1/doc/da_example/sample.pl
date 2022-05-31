#!/usr/bin/perl -w

my $bm = "../../bin/bm";

my $model       = "DisasterArea";
my $scenario    = "DA";
my $nodes       = 150;
my $x           = "350";
my $y           = "200";
my $groupchange = "0";
my $groupsize   = "1";
my $dist        = "3";
my $mindist     = "3";
my $circlevertices = "140";
my $factor      = "1";
my $duration    = "3000";
my $skip        = "5000";
my $seed        = "23";
my $maxpause    = "20";

################################## AREAS #########################################

my $maxareas    = "8";

my $IL1         = "25,5,125,5,125,100,25,100,25,50,125,50,0,15,15";
my $PWFTA1      = "133,25,180,25,180,75,133,75,133,50,180,50,1,39,37";
my $CCS1        = "220,5,300,5,300,40,220,40,220,20,310,20,2,15,0";
my $CCS2        = "220,46,300,46,300,80,220,80,220,60,310,60,2,15,0";
my $CCS3        = "220,86,300,86,300,120,220,120,220,100,310,100,2,15,0";
my $CCS4        = "220,126,300,126,300,160,220,160,220,140,310,140,2,15,0";
my $TEL         = "320,75,345,75,345,100,320,100,330,75,330,76,3,6,0";
my $APP1        = "320,5,345,5,345,50,320,50, 5,0,345,0, 325,5,325,50, 4,30,28";
my $OBST1       = "25,150,100,150,100,200,25,200";

###################################################################################

my $measures    = "10";
my $timestep    = "1";

my $params = "-f $scenario DisasterArea -n $nodes -x $x -y $y -p $maxpause -a $groupsize -g $circlevertices -r $dist -q $mindist -d $duration -e $maxareas -i $skip -j $factor -b $TEL -b $CCS1 -b $CCS2 -b $CCS3 -b $CCS4 -b $PWFTA1 -b $IL1 -b $APP1 -o $OBST1 -K -R$seed";

system "$bm $params";

exit(0);

