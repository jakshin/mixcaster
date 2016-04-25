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

You can run the program as a server, and it will listen for HTTP requests, serving podcast RSS feeds
and related music files in response (once they've been scraped and downloaded from Mixcloud).
See below for details on how to do so.

You can also run the program on the command line, to scrape a single Mixcloud artist/feed page,
and download all related music tracks.

Downloads from Mixcloud take quite a while - the first ~5 MB is typically sent very quickly,
but the rest of the download is then severely rate-limited - so multiple downloads are performed in parallel.
This is true whether the downloads are kicked off via an HTTP request for an RSS feed,
or by scraping a single feed on the command line.

Coming up:

* Detailed logging

* Convenient commands to install as a launchd service, allowing the program to easily be running in the background
  at all times (on OS X)

### How do I use it?

Once you've compiled the program (details on that coming soon),
check its config file, MixcloudPodcast.properties, and make any desired changes, then run it like so:

```
java -jar MixcloudPodcast.jar -service
```

Then, you can subscribe to a local URL in iTunes, which will be mapped to a Mixcloud artist feed.
For example, to subscribe to https://www.mixcloud.com/SomeArtist/ as a podcast,
subscribe to this URL in iTunes: http://localhost:25683/SomeArtist/podcast.xml.

When you've first subscribed, none of the feed's tracks will have been downloaded yet,
so every podcast episode's title will end with "[DOWNLOADING, CAN'T PLAY YET]".
Trying to play those episodes won't work - just wait awhile, and when iTunes next refreshes the podcast
(or when you refresh it manually if you're the impatient type), some or all of the tracks will have
finished downloading, and you'll be able to play them.
