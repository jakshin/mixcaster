# MixcloudPodcast

A "proxy" server which turns Mixcloud artist feeds into RSS-based podcasts (work in progress).

### What's all this, then?

I enjoy listening to music on Mixcloud, but have been annoyed that I can only stream it in a browser or mobile app.
I'd prefer to download it so that I could listen to it when I don't have a network connection.
This program is my answer to that desire: it scrapes Mixcloud artist/feed pages for info,
then makes that info available as podcast RSS feeds via a little built-in web server.
This makes it possible to subscribe to those RSS feeds in iTunes and on an iPhone,
download the music automatically when it becomes available, and take it anywhere.

The program is written in Java, so it should in theory be possible to make it work on any modern OS,
but I've only tested it on OS X, and wouldn't be at all surprised to find that it breaks in various odd ways
on any other OS. Java 1.7.0 or greater is required.

### What's the current status?

It's currently possible to run the program on the command line, to scrape a single Mixcloud artist/feed page,
and download all related music tracks. Downloads from Mixcloud take a while - the first ~5 MB is typically
sent very quickly, and the rest of the download is then severely rate-limited - so multiple downloads are
performed in parallel.

Coming up:

* A minimal built-in HTTP server to serve the scraped Mixcloud data as RSS XML, and downloaded music files
  (they must be both downloaded locally and re-served over HTTP, as Mixcloud blocks downloads from iTunes,
  presumably based on User-Agent, and iTunes won't load file URLs)

* Detailed logging

* Convenient commands to install as a launchd service, allowing the program to easily be running at all times
  (on OS X...)
