#!/bin/sh

size=64
dpi=xhdpi

for file in `ls ic_*.svg`
do
	basename=`basename $file .svg`
	inkscape --export-png=../res/drawable-$dpi/$basename.png --export-width=$size --export-height=$size --export-background-opacity=0,0 -C -z $file
done
