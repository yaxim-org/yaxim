yaxim (Yet Another XMPP Instant Messenger)
==========================================

yaxim is a Jabber/XMPP client with open source (GPLv2). Why pay for SMS if you can have unlimited messaging on your data plan?

yaxim aims at security, low overhead and keeping your server connection open. So far, it only supports a single account.


## Features

The following is already supported:

 * Connection with a single XMPP server (or GTalk, or Facebook Chat, or ...)
 * You are asked about self-signed SSL certificates
 * Allows automatic connection after turning on your phone
 * Reconnect on 3G/WiFi network change
 * Chat with your buddies (all messages are stored)
 * Adding/removing buddies from the roster
 * Delivery receipts (XEP-0184)
 * Message carbons (XEP-0280)


## Links

 * [Download APK](http://duenndns.de/yaxim/yaxim-current.apk)
 * [Screenshots](https://github.com/pfleidi/yaxim/wiki/Screenshots)
 * [Google Play](https://play.google.com/store/apps/details?id=org.yaxim.androidclient)
 * [Project Home](https://github.com/pfleidi/yaxim/wiki)
 * [Source on GitHub](https://github.com/pfleidi/yaxim)
 * [Translation](https://translations.launchpad.net/yaxim/master)


## Build Instructions

yaxim is written in Java and source code is maintained in `git`. The official
repository is [pfleidi's](https://github.com/pfleidi/yaxim), most development
work happens in [ge0rg's repo](https://github.com/ge0rg/yaxim). You will need
the Android SDK and `ant` to compile.

To compile yaxim, the following steps need to be taken:

	# fetch source code from github
	git clone git@github.com:pfleidi/yaxim.git
	cd yaxim
	
	# initialize submodules
	git submodule init
	git submodule update
	
	# prepare android build (with subprojects)
	android update project -p . -s
	android update project -p ActionBarSherlock/library
	android update project -p MemorizingTrustManager
	
	# compile debug version
	ant proguard debug
	
	# compile release version
	ant proguard release


## Building without `ant`

If you are using a different build environment, consider the following things:

 * You need to generate `res/values/version.xml` containing the `build_version` string. The template is in `version.xml.tpl`.
 * Without ProGuard, the compiled binary will be around 30% larger.

See also the [Setting up Eclipse](https://github.com/pfleidi/yaxim/wiki/Setting-up-Eclipse) wiki page.

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
