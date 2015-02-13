# escurel

Secure Quick Reliable Login (SQRL) support for Clojure.

Steve Gibson's [SQRL](https://www.grc.com/sqrl/sqrl.htm) technology
offers a new approach to web identity without passwords.
The **escurel** library provides an implementation of SQRL
using the latest Clojure and ClojureScript user interface
framework [Om](https://github.com/swannodette/om).

The word "[squirrel](https://en.wikipedia.org/wiki/Squirrel)",
first specified in 1327, comes from Anglo-Norman
esquirel from the Old French *escurel*, the reflex of a Latin word
sciurus.

## Running

Download the React javascript library:
```
cd resources/public/js/
wget http://fb.me/react-0.11.1.js
```

Have [Leiningen](https://github.com/technomancy/leiningen) auto build ClojureScript sources:
```
cd ../../..
lein cljsbuild auto escurel &
```

Open ```http://localhost:8080``` in your favorite browser:
```
open http://localhost:8080
```

## References

* Steve Gibson's [Secure Quick Reliable Login (SQRL)](https://www.grc.com/sqrl/sqrl.htm) technology
* David Nolen's [Om](https://github.com/swannodette/om) - A
  [ClojureScript](http://github.com/clojure/clojurescript) interface
  to [Facebook's React](http://facebook.github.io/react/).
* [EGD](http://egd.sourceforge.net/): The Entropy Gathering Daemon

Here is a fantastic guide to SQRL

* http://sqrl.pl/guide/

Here is a working client (for Android)

* https://play.google.com/store/apps/details?id=net.vrallev.android.sqrl

Here are three (mostly) working servers:

* http://sqrl-login.appspot.com/
* http://sqrl.pl/sandpit/
* http://sqrl-test.paragon-es.de/ (didn't seem to auto-login?)


## Copyright and license

Copyright © 2014-2015 Tom Marble

Licensed under the EPL (see the file [EPL](https://raw.githubusercontent.com/tmarble/escurel/master/EPL)).
