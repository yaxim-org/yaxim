yaxim (Yet Another XMPP Instant Messenger)
==========================================

yaxim is a lean Jabber/XMPP client for Android. It aims at usability, low overhead and security, and works on low-end Android devices starting with Android 4.0.

Check the [yaxim homepage](https://yaxim.org/) for latest news and downloads.

## Features

 * Easy configuration and usability, using one XMPP account
 * Keeping your connection when the phone is started and on mobile/WiFi network changes
 * Chatting with friends and in group chats
 * Sending and receiving of text messages, images and files
 * Managing of your contact list
 * [more...](https://yaxim.org/features/)


## Links

 * [Google Play](https://play.google.com/store/apps/details?id=org.yaxim.androidclient) (recommended, with auto-updates)
 * [F-Droid](https://f-droid.org/repository/browse/?fdid=org.yaxim.androidclient) (this version uses a different signing key, not interchangeable with official yaxim binaries!)
 * [Download APK](http://yax.im/apk)
 * [Screenshots](https://yaxim.org/screenshots/)
 * [Project Home](https://yaxim.org/)
 * [Source on GitHub](https://github.com/yaxim-org/yaxim)
 * [Translation](https://translations.launchpad.net/yaxim/master/+pots/yaxim/)


## Build Instructions

yaxim is written in Java and source code is maintained in `git`. The official
repository is [yaxim-org](https://github.com/yaxim-org/yaxim), whereas
experimental development work happens in
[ge0rg's repo](https://github.com/ge0rg/yaxim). You will need Android Studio 3.x
to compile the app.

To compile yaxim, the following steps need to be taken:

	# fetch source code from github
	git clone https://github.com/yaxim-org/yaxim.git
	cd yaxim
	
	# initialize submodules
	git submodule init
	git submodule update
	
	# you can stop here, or go on to compile with gradle:

	# compile and install debug version of yaxim or Bruno
	gradle installYaximDebug
	gradle installBrunoDebug
	
	# compile release APKs for both
	# create RELEASE_STORE_FILE according to http://stackoverflow.com/a/25391891/539443
	gradle assembleRelease


## License

 * yaxim is licensed under GNU GPLv2 (see LICENSE.txt)

 * [MemorizingTrustManager](https://github.com/ge0rg/memorizingtrustmanager) is MIT licensed.

 * [android-remote-stacktrace](http://code.google.com/p/android-remote-stacktrace/) is MIT licensed.


### MIT License

*android-remote-stacktrace*: Copyright (c) 2009 Mads Kristiansen, Nullwire ApS

*MemorizingTrustManager*: Copyright (c) 2010 Georg Lukas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
