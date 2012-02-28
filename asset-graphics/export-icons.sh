#!/bin/sh

# convert a single svg file into a png in the according drawable dir
svg2png() {
	basename=$1
	size=$2
	dest=$3
	inkscape --export-png=../res/$dest/$basename.png --export-width=$size --export-height=$size --export-background-opacity=0,0 -C -z $basename.svg
}

svg2icon() {
	basename=$1

	svg2png $basename 36 drawable-ldpi
	svg2png $basename 48 drawable
	svg2png $basename 72 drawable-hdpi
	svg2png $basename 96 drawable-xhdpi
}

# a status icon is 66% of a regular icon
svg2status() {
	basename=$1

	svg2png $basename 24 drawable-ldpi
	svg2png $basename 32 drawable
	svg2png $basename 48 drawable-hdpi
	svg2png $basename 64 drawable-xhdpi
}

# convert icon
#svg2icon icon

# convert status
# convert paw status
for file in `ls ic_*.svg`
do
	basename=`basename $file .svg`
	svg2status $basename
done
