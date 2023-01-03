<div align="center">

<img width="120" src="/misc/icon/overall.svg" />

Debatekeeper &mdash; a timekeeper for debate speeches
=====================================================

</div>

Debatekeeper is an Android app that times speeches in debates and rings bells automatically at the
correct times.  It supports most parliamentary styles of debating, including British Parliamentary,
Australasian, Asian, Australian, American Parliamentary, certain forms of Canadian Parliamentary and
all New Zealand styles.

The app rings a bell, vibrates and/or flashes the screen white (or any combination of the three) at
bell times.  It also rings overtime bells, which are configurable.

A small amount of documentation for this app is at https://github.com/czlee/debatekeeper/wiki/.

The app is [available on the Google Play Store](https://play.google.com/store/apps/details?id=net.czlee.debatekeeper&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1).

<div align="center">
<a href="https://play.google.com/store/apps/details?id=net.czlee.debatekeeper&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1" target="_blank">
<img alt='Get it on Google Play' height="96" src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/></a>
</div>

What is the status of this app?
-------------------------------
As of January 2023, I'm working through the release process of version 1.4. This is mostly just to
comply with Android's [target API level
requirements](https://support.google.com/googleplay/android-developer/answer/11926878), so that the
app doesn't get pulled from the Play Store. I don't intend to do any other work on Debatekeeper,
other than maintaining the new [online formats
repository](https://github.com/czlee/debatekeeper-formats) and further target API updates.

The main motivation for the new online repository is to make it easier to make new debate formats
available. Before version 1.3, new formats were distributed with app updates, and there hadn't been
one for five years. I don't anticipate doing any further development on the app.

Consider this an open invitation for anyone else to pick up this project and run with it. It's
licensed under the GPLv3, so any prospective developer would be bound by those licensing terms.
You're welcome to contact me if you have any questions. See the [notes for prospective developers
below](#notes-for-prospective-developers).

Licence
-------
This app (and all source code, with exceptions noted below) is licensed under the GNU General Public
Licence version 3.  You can find a copy of this licence in the "licence.txt" file, or go to
http://www.gnu.org/licenses/gpl-3.0.html.

The exception is the bell sounds.  I bought the single bell sound from SFXSource (which appears to
be now defunct), so I can't make it freely available.  If you want to contribute and need this file,
get in touch with me.

Adding debate styles
--------------------
New debate styles should be submitted to the online debate formats repository at
https://github.com/czlee/debatekeeper-formats. Instructions are in that repository.

I've written a page on [how to write a debate format XML
file](https://github.com/czlee/debatekeeper/wiki/Writing-your-own-custom-debate-format-file). But
it's probably easiest in the first instance to look in the [formats
repository](https://github.com/czlee/debatekeeper-formats) for the XML files there, and modify one
of those.

This app doesn't do the public forum, Lincoln-Douglas or policy debate styles. There are other
Android apps that do this. The reason this app can't currently do this is that it doesn't currently
support preparation time that can be used by teams between speeches at their election. A discussion
of this is in [issue #6](https://github.com/czlee/debatekeeper/issues/6).

Notes for prospective developers
--------------------------------

I don't intend to work on this app beyond version 1.3 (October 2021), so if you're interested in
helping with development, I'd love for you to dive in and possibly take over the project. Contact me
if you want to discuss anything, or feel free to just fork the repository and get going.

_Note:_ The master branch has legacy support code for version 1.3 that should be removed in the next
version. I've done this on the [**remove-legacy**
branch](https://github.com/czlee/debatekeeper/tree/remove-legacy), so for any non-minor development,
please start from that branch (and merge it into master).

### Files you need to build this project

To build this project, you'll need to:

1. Check out this repository (and put it somewhere useful)

2. Add the bell sounds, called `desk_bell.mp3`, `desk_bell_double.mp3` and `desk_bell_triple.mp3`,
   all to the `app/src/main/res/raw/` directory. The app won't build without a sound file of some
   sort there.  Any sound file will do.  I'm happy to provide the file to interested developers, on
   the understanding that it is _not_ available under a free-distribution license (as discussed
   above)—contact me at the details below.  Of course, future developers may also use other sounds,
   including more freely available ones, if they can find a satisfactory one.

### Sound file specifications

If you wish to use your own sound files for the `desk_bell*.mp3` files, here is some information
about them:
- The sound is a desk bell (as the name suggests), also known as a counter bell or call bell.
- The original `desk_bell.mp3` sound is about 2 seconds long, but this shouldn't in principle
  matter; there is code that stops any existing playback if a new one needs to be started.
- The `desk_bell_double.mp3` and `desk_bell_triple.mp3` sounds are just the `desk_bell.mp3` sound,
  but repeated at an interval of 0.5 seconds. Any sound editing tool should be able to do this; I
  used [Audacity](https://www.audacityteam.org/). The purpose of these files is just to make the
  interval more predictable and to avoid abrupt audio stops for common multiple-bell cases.

### Publishing to the Play Store

If you work on a significant update, I'll encourage you to list it under a separate Play Store
listing on your own account. To do this, you'll need to change the package name from
net.czlee.debatekeeper to something else. Feel free to rename/rebrand it if you like. Don't forget
that, under [the GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html), you're required to make your
code publicly available. Please credit me in your distribution (_e.g._ in the store listing).

For minor updates, I might consider pull requests and releasing it under the existing Play Store
listing. Just be aware that some pull requests have sat around for several years before I got a
chance to look at them, and this is likely to be longer in the future.

Please don't hesitate to email me if you have any questions about how to proceed with this. I'm
likely to take forever with reviewing code, but I should be able to respond to emails about a
developer transition much more quickly.

Contacting the author
---------------------
You can find my e-mail address by checking out this repository and looking at the commit authors, or
alternatively message me on Facebook (czlee) or Twitter (@czczlee).
