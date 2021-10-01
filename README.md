# Mixcaster

<!--suppress HtmlDeprecatedAttribute, CheckImageSize -->
<img src="src/main/resources/jakshin/mixcaster/http/logo.png" align="right" alt="" height="128">

Mixcaster is a "proxy" server which allows subscribing to Mixcloud users' music as RSS podcasts, and downloading Mixcloud music files, so you can listen to them offline.

You can subscribe to any of the feeds/collections of music that the Mixcloud user has made available: their stream, shows, favorites, listening history, or playlists. You can then listen to them in the podcast client of your choice.

### Why does this exist?

Some of my favorite music is on Mixcloud, and I like to listen to it in situations where I have little or no network connectivity. When users have joined Mixcloud Select, the Mixcloud mobile app lets you [download their music](https://help.mixcloud.com/hc/en-us/articles/360004054359-How-do-I-listen-offline-) and listen offline, which is great — subscribing is a fantastic way to support those artists, and gain the convenience of offline use. But not all Mixcloud users have joined Select, including many of the ones whose music I like most. This app lets me listen to those users' music offline too.

### What does it do?

Mixcaster has two modes of operation: You can run it as a local server, and it will listen for HTTP requests, serving podcast RSS feeds and related music files in response (downloading from Mixcloud as needed); and then you can subscribe to its RSS feeds' URLs in a podcast client. Or, you can run it at a command line, to download some of a Mixcloud user's music to local files, for use however you like.

Downloads from Mixcloud take quite a while — the first ~5 MB is typically sent very quickly, but the rest of the download is then severely rate-limited — so multiple downloads are performed in parallel.

### Installation and use as a service

After downloading a release, unzip it to wherever you'd like to keep it, and take a glance through its settings in `mixcaster-settings.properties`, tweaking them as desired. Then open a terminal and run `mixcaster -service` to run the service, or `mixcaster -install` to set the service up as a launchd agent which will run whenever you're logged in.

Then open your podcast app and add a URL. For example, in macOS's Podcasts app, use File > Add a Show by URL; in iOS's, use Library > (...) button in the upper right-hand corner > Add a Show by URL.

The Mixcaster URL's hostname is `localhost` if your podcast app is running on same machine as Mixcaster, or something like MachineName.local if you're on a different device connected to the same network, like say a mobile device. Mixcaster runs on port 6499 by default (configurable in `mixcaster-settings.properties`), so add that after the hostname.

The URL's path starts with the Mixcloud user's username, which you can see in their URL. Then add which music collection you want to subscribe to: `stream`, `shows`, `favorites`, `history`, or `playlist`. You can use `uploads` as a synonym for `shows`, and `listens` as a synonym for `history`. If it's a playlist you're interested in, you'll need to also add the last path component from its Mixcloud URL. And lastly, you can add `.xml` at the end if it makes your podcast client happier.

So for example, let's say you want to subscribe to Armada Music's shows. Their Mixcloud URL is https://www.mixcloud.com/ArmadaMusicOfficial/, so their username is `ArmadaMusicOfficial`. You can use any of these URLs to subscribe to their shows in Mixcaster:
* http://localhost:6499/ArmadaMusicOfficial/shows/
* http://localhost:6499/ArmadaMusicOfficial/shows.xml
* http://localhost:6499/ArmadaMusicOfficial/uploads/
* http://localhost:6499/ArmadaMusicOfficial/uploads.xml

Want to subscribe to their [Armada Trance Mixes](https://www.mixcloud.com/ArmadaMusicOfficial/playlists/armada-trance-mixes/) playlist? Its URL ends with "armada-trance-mixes", so you can use Mixcaster URLs like these (`playlist` and `playlists` are interchangeable here):
* http://localhost:6499/ArmadaMusicOfficial/playlists/armada-trance-mixes/
* http://localhost:6499/ArmadaMusicOfficial/playlist/armada-trance-mixes.xml

[Armin van Buuren](https://www.mixcloud.com/ArminvanBuuren/) and [Jose Solis](https://www.mixcloud.com/DJoseSolis/) are both on Mixcloud Select, so you can subscribe to access their shows offline. But what if you wanted to subscribe to their favorites or listening history?
* http://localhost:6499/ArminvanBuuren/favorites/
* http://localhost:6499/DJoseSolis/listens/
* http://localhost:6499/DJoseSolis/history.xml

You can also use a Mixcaster URL containing only a username, and Mixcaster will figure out their default view (e.g. `stream` or `shows`), and return a podcast containing it. So for example: 
* http://localhost:6499/Trance19800524/

When you first subscribe to a user's music, Mixcaster won't have had a chance to download their music files from Mixcloud yet. Mixcaster tries to make this clear by appending `[DOWNLOADING, CAN'T PLAY YET]` to the title of any episode whose music file isn't fully downloaded yet, because trying to play those podcast episodes won't work. Just wait a few minutes, and when your podcast app next refreshes the podcast (or when you refresh it manually, if you're the impatient type), some or all of the episodes' files will have finished downloading, and you'll be able to play them.

By the way, the first request to Mixcaster for a podcast's RSS XML, before its music files have been fully downloaded, also runs a bit slowly — taking 10 seconds or more to complete — because Mixcaster has to issue a HEAD request to Mixcloud's servers for each music file in order to populate the podcast's list of episodes.

One way to minimize both of those minor hassles is to first download the music you're interested in at a command line, before subscribing in your podcast app. Which brings us to...

### Downloading music from a command line

Building a command line to download music works much like building a URL to subscribe to a podcast. Start with `mixcaster -download`, then add a Mixcloud username, then optionally the music collection of interest (`shows`, `favorites`, etc.), or leave that part off to get music from the user's default view, which is likely `stream` or `shows`. If you're downloading a `playlist`, you'll need to also give the last path component of its URL.

For example:
* `mixcaster -download Trance19800524`
* `mixcaster -download ArminvanBuuren favorites`
* `mixcaster -download ArmadaMusicOfficial playlist armada-trance-mixes`

Run `mixcaster -help` for full usage details, including options which control how many music files to download, and where to put them.

### A note about episode sorting

Podcast apps tend to sort episodes by their publication date. This works out well when you subscribe to a user's stream or shows, which Mixcloud also shows in publication-date order. 

Mixcloud shows users' favorites and listening history sorted by the date when they were favorited or listened to, though, and that date is not exposed in Mixcloud's GraphQL API. Similarly, playlists have an arbitrary user-defined order. In each of these cases, Mixcloud's website preserves that sorting by showing items in the order they're returned by Mixcloud's APIs. Mixcaster takes the same approach, and also retains Mixcloud's ordering in the RSS XML it generates — but podcast apps generally ignore the "physical" ordering of the podcast's XML, and instead sort episodes by their original publication date.

Long story short, podcast apps usually show podcasts containing users' favorites, history and playlists sorted by the music's original publication date, not by the date it was favorited or listened to by the user. This hasn't bothered me enough to do anything about it. 🤷‍

### Other things to be aware of

If you move Mixcaster to a different folder:
* Keep its files together; for instance, if its properties file isn't in the same folder as its jar file, it won't be found, and default settings will be used instead
* If you've installed it as a launchd agent, re-run `mixcaster -install` from its new location to keep the launchd agent working

You can remove the launchd agent with `mixcaster -uninstall`.

The program is written in Java, so it should in theory work on any modern OS, but I've only tested it on macOS, and wouldn't be surprised to learn that it breaks in various odd ways on another OS, especially Windows.

### Contributing

I made Mixcaster for my own purposes, but if it's useful to you, that's great! If it's _almost_ useful to you, but you wish it behaved just a little differently, feel free to ask. And even better, if you'd like to tweak or improve it, and you think others would like your changes, well then your pull request is welcome! 🙂
