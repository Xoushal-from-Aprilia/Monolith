# Developer key

`developer.pub` holds the public code of the developer dashboard (Monolith Dev): 64 hex characters on
one line. Every Monolith app (2.4.0 and later) downloads it from this repository, at most every 12 hours,
and from then on follows the document that key signs: news, newest and minimum version, blocked accounts.
Reports and messages from the apps are encrypted to the same key.

- No file, or an empty one: the developer channel is off.
- New key (lost phone, new dashboard): put the new public code here; the old key stops counting.
- Changes here do not start a new build of the app.
- Never put the secret code anywhere in this repository.
