#!/bin/sh

# convert a single svg file into a png in the according drawable dir
svg2png() {
	basename=$1
	width=$2
	height=$3
	dest=$4
	api=$5
	inkscape --export-png=res/$dest$api/$basename.png --export-width=$width --export-height=$height --export-background-opacity=0,0 -C -z asset-graphics/$basename$api.svg
}

# convert a single svg into a group of PNGs for all DPIs
svg2all() {
	basename=$1
	dpx=$2
	dpy=$3
	api=$4
	while read scale dir ; do
		svg2png $basename $(($dpx*$scale)) $(($dpy*$scale)) drawable$dir $api
	done <<EOF
1
3/2 -hdpi
2 -xhdpi
3 -xxhdpi
4 -xxxhdpi
EOF
}

# convert SVG to launcher icons (sizes at http://stackoverflow.com/a/12768159/539443)
svg2launcher() {
	basename=$1
	api=$2

	svg2all $basename 48 48 $api

	svg2png $basename 512 512 ../asset-graphics/
}

# a roster status icon is 66% of a launcher icon, making it 32dp
# action bar icons are 32dp as well
svg2rosteraction() {
	basename=$1

	svg2all $basename 32 32
}

# a notification bar icon is 16x25dp (pre-v11), or 24dp as of v11
svg2notif() {
	sbname=$1

	svg2png $sbname 16 25 drawable
	svg2png $sbname 24 38 drawable-hdpi
	svg2png $sbname 32 50 drawable-xhdpi

	svg2all $basename 24 24 -v11
}

svg2chat() {
	svg2all $1 18 10
}

# convert chat markers
for file in asset-graphics/ic_chat_*.svg
do
	basename=`basename $file .svg`
	svg2chat $basename
done

# convert launcher icon
svg2launcher icon

# convert GNU for about dialog
svg2all gnuicon 48 48

# convert statusbar notification icons
svg2notif sb_message
svg2notif ic_online
svg2notif ic_offline

# convert roster status
for file in asset-graphics/ic_action_*.svg asset-graphics/ic_status_*.svg
do
	basename=`basename $file .svg`
	svg2rosteraction $basename
done

