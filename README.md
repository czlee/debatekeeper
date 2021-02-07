Debatekeeper &mdash; a timekeeper for debate speeches
=====================================================

Debatekeeper is an Android app that times speeches in debates and rings bells
automatically at the correct times.  It supports most parliamentary styles
of debating, including British Parliamentary, Australasian, Asian, Australian,
American Parliamentary, certain forms of Canadian Parliamentary and
all New Zealand styles.

The app rings a bell, vibrates and/or flashes the screen white (or any
combination of the three) at bell times.  It also rings overtime bells,
which are configurable.

The website for this app is at http://tryingtoreason.wordpress.com/debatekeeper.

You can install the app on the Google Play Store:

<a href='https://play.google.com/store/apps/details?id=net.czlee.debatekeeper&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' height='72px' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/></a>

What is the status of this app?
-------------------------------
_Last updated: 7 February 2021_

It hasn't been updated since 2016. Since then, my debating software priorities
have mostly been with [Tabbycat](https://github.com/TabbycatDebate/tabbycat),
and since 2019, my life priorities have generally been outside debating.

Since then, Android's moved through a few versions, so the next task will be
to update the source code to support the latest versions and to follow current
Android project conventions. That basic maintenance work will need to precede
any of the currently pending (and future) pull requests, and because it's been
so long, I think it's a bit of a project in its own right. I don't know when 
I'll get to it, but I'm hoping I'll get a chance in the fourth quarter of
2021.

Consider this an open invitation for anyone else to pick this up and run with
it, if you're interested. It's licensed under the GPLv3, so any prospective
developer would be bound by those licensing terms. If you have any questions
about updating it to work with the current Android SDK, I'll probably be just
as lost as you, but you're still welcome to send me an email (find my email
address in the commit logs of this repository). You can create your own
Google Developer account and release it yourself, or if you've got the
repository to build on the latest version of Android Studio, I'm happy to see
if I can build it myself and push an APK under the official Google Play Store
entry (_i.e._, my one).

Licence
-------
This app (and all source code, with exceptions noted below) is licensed under
the GNU General Public Licence version 3.  You can find a copy of this licence
in the "license.txt" file, or go to http://www.gnu.org/licenses/gpl-3.0.html.

This app makes use of Mike Novak's
[NumberPicker for Android](https://github.com/mrn/numberpicker) library.
The NumberPicker library is licensed under the Apache 2.0 Licence (not
the GPLv3).  A handful of related files that I've written, but put in the
main repository, are also licensed under the Apache 2.0 Licence; those exceptions
are noted in the files in question.

The other exclusion in this repository is the bell sound.  I bought the bell
sound from [SFXsource](http://www.sfxsource.com/), so I can't make it freely
available.  If you want to contribute and need this file, get in touch with me.

Getting started
---------------
Before you can build this project, you'll need to:

1. Check out this repository (and put it somewhere useful)

2. Add the **numberpicker** library to your set-up.
This source code for the NumberPicker component isn't in this repository.
To build this app, you will need to check out [that repository](https://github.com/mrn/numberpicker)
and put it in **../numberpicker** (relative to the directory where this
repository is checked out on your computer).

	If you're using Eclipse with ADT, you'll then need to open the project in
	**../numberpicker/lib** (so you	now have two new projects in your workspace).
	Then add that library as an *Android Classpath Container* to the Java Build
	Path (via *Add Library&hellip;*) in the Debatekeeper project's properties.

	If you're using another environment, I can't help you,
	sorry.  I spent ages trying to figure out how to include this library, so
	if you have a better way, I'm really keen to hear from you.

3. Add the bell sound at **res/raw/desk_bell.mp3**.  You won't be able to build
this app without a sound file of some sort there.  Any sound file will do, but
if you want to help with this app, it'll probably be useful for it to be the same
one I'm using.  In that case, contact me at the details below.

Adding debate styles
--------------------
I've included all the parliamentary debate styles I can find, but I
obviously don't know about all the different styles in the world.  If
there's a style you'd like me to add, please do any of the following,
in order of preference:

1. Fork this repository, add your debate format XML file to the assets
directory, and submit a pull request.  This is preferred because it's
easiest for me and means your contribution is recorded in the
repository commits.

2. Send me your debate format XML file (contact details below), and
I'll add it to the repository.

3. Describe the debate style to me (speech times, bell times, names
of positions, _etc._) at the contact details below and I'll write
the XML file and add it to the repository.

I've written a page on [how to write a debate format XML file](https://github.com/czlee/debatekeeper/wiki/Writing-your-own-custom-debate-format-file).
But it is probably easiest in the first instance to look in the **assets/formats**
directory for the XML files there, and follow those.

This app doesn't (yet) do the public forum, Lincoln-Douglas or policy
debate styles.  There are other Android apps that do this.  The reason
this app can't currently do this is because it doesn't currently support
preparation time that can be used by teams between speeches at their
election.  I may or may not add this in future.

Contributions
-------------
If you want to contribute to this project (in other ways), I'm keen to
hear from you.  Contact me at the details below, or just fork this
repository and then submit a pull request.

Contacting the author
---------------------
You can find my e-mail address by checking out this repository and
looking at the commit authors, or alternatively message me on
Facebook (cz.lee) or Twitter (@czlee11).
