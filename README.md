# notPipe
**English** / [русский](README.ru.md)

A working YouTube client for **Android 1.5+** utilizing multiple backend APIs. Made with ❤ and longevity in mind. Instead of depending on a single instance that might suddenly fail, the app leverages multiple instances concurrently for maximum reliability.
* **Telegram channel for updates**: [@AppDataApps](https://t.me/AppDataApps)
* Join our **[Retro Android Group](https://t.me/retroandroidgroup)** on Telegram!
* Discord server: [Android Afterlife](https://discord.gg/2JqfEkQyck)

![notPipe](img/logo.png "Working YouTube for legacy Android devices")

<img src="img/scr1.png" alt="Screenshot" width="200"/> <img src="img/scr2.png" alt="Screenshot" width="200"/> <img src="img/scr3.png" alt="Screenshot" width="200"/> <img src="img/scr4.png" alt="Screenshot" width="200"/> <img src="img/scr5.png" alt="Screenshot" width="200"/> <img src="img/scr6.png" alt="Screenshot" width="200"/>

List of supported APIs:
* [Invidious](https://docs.invidious.io/api/) (metadata, 360p)
* [yt2009](https://github.com/ftde0/yt2009) (all qualities, conversion, channels, authorization)
* [Piped](https://docs.piped.video/docs/api-documentation/) (metadata, 360p)
* [TypeType](https://typetype.video) (metadata, all qualities, channels)
* YtAPILegacy (metadata, all qualities, conversion)

## 📥 Download
* [GitHub Releases](https://github.com/gohoski/notPipe/releases)
* [OldMarket](http://oldmarket.xyz/app.php?p=io.github.gohoski.notpipe)
* [MyIntCountry](http://myintcountry.ru/index.php?board=android&action=display&num=1)
* Telegram (link at the top of the README)
* [4PDA](https://4pda.to/forum/index.php?showtopic=1119054)

## Features
> [!NOTE]  
> This app does not connect to YouTube directly, which can bypass various network restrictions. See ["Why and how"](#why-and-how) for more information.
* Popular, search
* Videos, related videos, comments
* Video playback in 360p/480p/720p/1080p
* Channels
* Video conversion for devices that do not support H.264 to MPEG-4 Visual and H.263 codecs
* YouTube account login via yt2009 (recommendations, subscriptions, history, etc.)
* Automatic updates of the list of instances from a URL
* Video downloader
* Tablet UI
### TODO
* Audio downloader
* Playlists
* Local account for subscriptions (NewPipe-like)
* SponsorBlock
* Return YouTube Dislike
* Audio background playback

## Solving issues on Android ≤2.3
*(videos lag or do not play at all)*

If videos do not play, first try typical diagnostics:
1. **Switch an instance.** Click the "Playback via … >" button. Some instances might be temporarily unavailable.
2. Make sure your **network is stable.** notPipe may not work properly on an unreliable Internet connection.

If this does not help, then your device does not support the H.264 codec (typically non-ARMv7). Its support depends heavily on the device. There are two ways to solve this problem in notPipe:
1. **Use [MX Player](https://files.catbox.moe/mhq8qr.7z)** (Android 2.1+) and switch to the **external player** in settings. However, on unpowerful devices, it's likely that the player will lag on most videos. You can only try.
2. **Enable conversion** in settings. This converts the video to the MPEG-4 Visual codec server-side while using the system player. Requires an SD card. This will unfortunately make a delay when playing a video, but it should last no more than 3–5 minutes.

## Why and how
Existing methods for watching YouTube on legacy Android suffer from two fatal flaws—poor usability and an inability to survive YouTube’s current anti-bot measures. Current workarounds generally have the following issues:
* **Single point of failure.** Previous ways of watching YouTube rely on a single specific API instance. The moment an instance gets a noticeable amount of traffic, YouTube automatically bans its IP address. A single instance may also be heavily overloaded, rendering it unusable.
* **Poor usability.** Web wrappers like S60Tube lack a native feel and require constantly switching between a browser and a video player. yt2009 patched APKs require tedious, per-instance patching—furthermore, they often suffer from heavy server-side video transcoding overhead, crippling in endless buffering. The old mobile YouTube design also isn't always practical. Everything feels clunky.

This project was built from the ground up to solve these exact issues. Instead of relying on a single point of failure, this client **utilizes multiple APIs and instances** simultaneously, randomly selecting a new instance for each activity. This decentralized approach provides critical advantages:
1. **Ban evasion.** Requests are distributed across a big enough pool of servers. Because no single server generates massive traffic, the chances of YouTube triggering an IP ban are drastically reduced.
2. **Performance.** Server bandwidth is load-balanced naturally, preventing the crippling slowdowns seen in single-instance patched APKs.
3. **It just works.** You get a native Android interface without needing to patch your own APKs or configure private servers.
4. **No single point of failure.** Because this is exactly how previous YouTube clients died, everything is decentralized and open for anyone to contribute.
5. **Automatic updates of the instances.** The list of the instances is automatically updated and by default loads from http://144.31.189.129/notPipe.json, requiring as less actions from the user as possible.

## Reporting bugs
**Report bugs in the [Issues](https://github.com/gohoski/notPipe/issues) tab!** Don't forget to specify which version of Android you encountered the bug on.

## Build
The project is developed under the following build environment.
* Android Studio 2.3.2 [`Download`](https://developer.android.com/studio/archive)
  * Android Studio 1.0–3.1.2 may support Android <2.2, but 2.3.2 is recommended for development as it's simultaneously old and supported.
  * Latest AS versions still support Android 2.2 and later (though they are made with 4.1+ in mind)—you can use them if you don't prioritize old Android versions.
* Android SDK of any version *(25 recommended)*
  * It is not required to use an old SDK for developing legacy apps.
* Android 1.5/1.6 emulator from the SDK [`Download`](https://developer.android.com/sdk/older_releases#release-1.6-r1)

It is recommended to use AS while contributing; however, you may use another IDE as long as you make the project still usable in AS.

## Acknowledgments
* [How-to-develop-and-backport-for-Android-2.1-in-2020](https://github.com/Mik-el/How-to-develop-and-backport-for-Android-2.1-in-2020) project template by Michele
* [NNJSON](https://github.com/shinovon/NNJSON) library by nnproject
* **Special thanks to all [Invidious](https://github.com/iv-org/invidious/graphs/contributors?all=1), [yt2009](https://github.com/ftde0/yt2009/graphs/contributors?all=1), [TypeType](https://github.com/TypeType-Video/TypeType-Server/graphs/contributors?all=1) and [Piped](https://github.com/TeamPiped/Piped-Backend/graphs/contributors?all=1) contributors for making these awesome APIs possible**
### Previous YouTube clients
Although not used as inspiration or a codebase, these other attempts of implementing YouTube deserve acknowledgment, as they led to the idea of this project.
* [Mini YouTube by monobogdan](https://github.com/monobogdan/selfeco) — client for Android 2.0+ utilizing a hardcoded Invidious instance with requests proxied to monobogdan's Russian server. The proxy server is dead and considering it has a single point of failure, it's not worth it to compile the project with a new instance even with the proxy removed.
* [ReOldTube by YMP Yuri](https://github.com/YMP-CO/ReOld-Tube) — client for Android 3.0+ utilizing Invidious. While it can be configured in settings to be used today, it has numerous bugs *(and is vibecoded)*.
* [YT-Legacy-Eclair by MoreVista](https://github.com/MoreVista/YT-Legacy-Eclair) — simple client for Android 2.1+ utilizing Invidious. The single instance can be changed in settings.
## License
The **notPipe** project is licensed under the Do What The Fuck You Want To Public License, Version 2. See [LICENSE](LICENSE) for details. *If you want, you may credit me in the README of your project.*  

HOWEVER, the NNJSON library is licensed under the MIT license. See [LICENSE-NNJSON](LICENSE-NNJSON) for details.