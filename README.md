Debatekeeper &mdash; a timekeeper for debate speeches
=====================================================

Debatekeeper is an Android app that times speeches in debates and rings bells automatically at the
correct times.  It supports most parliamentary styles of debating, including British Parliamentary,
Australasian, Asian, Australian, American Parliamentary, certain forms of Canadian Parliamentary and
all New Zealand styles.

The app rings a bell, vibrates and/or flashes the screen white (or any combination of the three) at
bell times.  It also rings overtime bells, which are configurable.

A small amount of documentation for this app is at https://github.com/czlee/debatekeeper/wiki/.

The app is [available on the Google Play Store](https://play.google.com/store/apps/details?id=net.czlee.debatekeeper&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1).

<a href="https://play.google.com/store/apps/details?id=net.czlee.debatekeeper&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1" target="_blank">
<img alt='Get it on Google Play' height="96" src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/></a>

Some kind people have also built this app and made it [available on F-Droid](https://f-droid.org/packages/net.czlee.debatekeeper),
though I'm not involved in that distribution, so I'm not in a position to vouch for it.

<a href="https://f-droid.org/packages/net.czlee.debatekeeper" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="96"/></a>

What is the status of this app?
-------------------------------
_9 October 2021:_ After five years, an update is forthcoming. It's currently in beta testing, and
will be released in late October 2021. Users interested in [joining the beta testing programme may
do so here](https://play.google.com/apps/testing/net.czlee.debatekeeper).

I've decided to create a new [online formats
repository](https://github.com/czlee/debatekeeper-formats), which the app will query. All pull
requests with new formats will be asked to resubmit their formats to the new repository.

A lot of the motivation for this new online repository is to make it easier to make new debate
formats available. They were previously distributed with app updates, I haven't released an update
since 2016, and I don't imagine I'll be doing any more updates in the future.

Consider this an open invitation for anyone else to pick up this project and run with it. It's
licensed under the GPLv3, so any prospective developer would be bound by those licensing terms.
You're welcome to contact me if you have any questions (find my email address in the commit logs of
this repository). I'll probably encourage you to release it under a new Play Store listing that is
controlled from your account.

Licence
-------
This app (and all source code, with exceptions noted below) is licensed under the GNU General Public
Licence version 3.  You can find a copy of this licence in the "licence.txt" file, or go to
http://www.gnu.org/licenses/gpl-3.0.html.

The exception is the bell sounds.  I bought the single bell sound from SFXSource (which appears to
be now defunct), so I can't make it freely available.  If you want to contribute and need this file,
get in touch with me.

Getting started
---------------
Before you can build this project, you'll need to:

1. Check out this repository (and put it somewhere useful)

2. Add the bell sounds, called `desk_bell.mp3`, `desk_bell_double.mp3` and `desk_bell_triple.mp3`,
   all to the `app/src/main/res/raw/` directory. The app won't build without a sound file of some
   sort there.  Any sound file will do, but if you want to help with this app, it'll probably be
   useful for it to be the same one I'm using.  In that case, contact me at the details below.

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

Contributions
-------------
I don't intend to work on this app beyond version 1.3 (October 2021), so if you're interested in
helping with development, I'd love for you to dive in and possibly take over the project. Contact me
if you want to discuss anything, or feel free to just fork the repository and get going.

You won't be able to release to the Play Store under the same listing, because you don't have access
to my account. If you work on a significant update, you're welcome to release it under a separate
Play Store listing.

Contacting the author
---------------------
You can find my e-mail address by checking out this repository and looking at the commit authors, or
alternatively message me on Facebook (czlee) or Twitter (@czczlee).
