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

[![Get this app on Google Play](http://www.android.com/images/brand/get_it_on_play_logo_large.png)](https://play.google.com/store/apps/details?id=net.czlee.debatekeeper)

Licence
-------
This app (and all source code, with exceptions noted below) is licensed under 
the GNU General Public License version 3.  You can find a copy of this licence
in the "license.txt" file, or go to http://www.gnu.org/licenses/gpl-3.0.html.

This app makes use of Mike Novak's
[NumberPicker for Android](https://github.com/mrn/numberpicker) library.
The NumberPicker library is licensed under the Apache 2.0 License (not
the GPLv3).

This source code for the NumberPicker component isn't in this repository.
To build this app, you will need to check out [that repository](https://github.com/mrn/numberpicker)
and put it in **../numberpicker** (relative to the directory where this
repository is checked out on your computer).

The other exclusion in this repository is the bell sound.  I bought the bell
sound from [SFXsource](http://www.sfxsource.com/), so I can't make it freely
available.  If you want to contribute and need this file, get in touch with me.
The file (any sound file will do) should go at **res/raw/desk_bell.mp3**.
(You'll probably find you can't build this app without a sound file there.)

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

I've written a page on [how to write a debate format XML file](http://tryingtoreason.wordpress.com/debatekeeper/writing-your-own-debate-format-xml-file/).
But it is probably easiest in the first instance to look in the **assets**
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