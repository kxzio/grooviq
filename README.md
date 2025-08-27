**grooviq - ultimate sound provider**

● fully-featured **MP3 music player for android**, built with **jetpack compose**.  
this player allows users to manage music efficiently, search for artists and albums, create playlists, and enjoy personalized recommendations.

  <img src="https://i.imgur.com/s9kXJsA.png" width="1000"/>
  
  ![platform](https://img.shields.io/badge/platform-android-lightgrey)
  
  ![language](https://img.shields.io/badge/language-kotlin-purple)
  ![python](https://img.shields.io/badge/python-3.10-yellow)
  
  ![version](https://img.shields.io/badge/version-1.0-blue)
  ![build](https://img.shields.io/badge/build-passing-brightgreen)
   
  ● *current version - 2.4.126 (recode) (last beta build)* 
  
  ● *current release - 1.0*
  
## features

- **queue management**: view the upcoming tracks in the queue (excluding the currently playing track) and manually reorder them. supports lazy loading for large playlists and ensures smooth ui performance.  

- **drag & drop**: reorder tracks in the queue with a simple drag gesture. includes haptic feedback for precise interaction.  

- **search**: search for artists, albums, or specific tracks. works both locally in your library and online using integrated apis.  

- **playlists**: create, edit, and manage custom playlists. tracks can be added from search results or the queue, reordered, and saved for later playback.  

- **recommendations**: receive personalized track recommendations based on your listening habits, playlists, and search history. designed to help discover new music while keeping user preferences in mind.  

<p float="left">
  <img src="https://i.imgur.com/HyJuxeX.jpeg" width="300" height = "330" style="margin-right: 10px;"/>
  <img src="https://i.imgur.com/7DNFmcL.jpeg" width="300" height = "330" />
</p>

- **streaming audio**: supports playback of streaming audio from online sources. tracks are buffered efficiently to prevent playback interruptions.  

- **audio preloading**: upcoming tracks are preloaded to ensure smooth transitions and minimize gaps between songs.  

- **download tracks**: users can download tracks for offline playback, including associated album art and metadata.  

- **smooth performance**: optimized for performance, including lazy loading of queues, efficient memory usage for album art, and fast search results.  

- **integration with online services**: uses python-based apis (requests) for online search, streaming, and metadata extraction. supports both direct audio streams and based sources.  

- **robust storage and serialization**: uses room database for saving playlists, queue state, and track metadata; serialization handled via kotlinx-serialization-json.  

---


<p float="left">
  <img src="https://i.imgur.com/J4dVLC0.jpeg" width="300" style="margin-right: 10px;"/>
  <img src="https://i.imgur.com/h5sa83r.jpeg" width="300" style="margin-right: 10px;"/>
  <img src="https://i.imgur.com/VsCeiEw.jpeg" width="300"/>
</p>

## technologies used

- **jetpack compose** – modern declarative ui framework for android
  
- **exoplayer / media3** – high-performance audio playback with support for streaming, buffering, and hls
   
- **room** – local database for saving playlists, queue state, and track metadata
  
- **chaquopy / python** – integration of python scripts for online api access:
  - requests – http requests  
  - mutagen – audio metadata processing  
  - syncedlyrics – synchronized lyrics extraction
    
- **retrofit** – type-safe http client for api communication
  
- **okhttp / okhttp-logging-interceptor** – efficient network requests with logging
  
- **jsoup** – html parsing for web scraping and metadata extraction
  
- **glide** – image loading and caching library
  
- **coil-compose** – lightweight async image loading for compose
  
- **sh.calvin.reorderable** – drag & drop reordering of list items in compose
  
- **kotlinx-serialization-json** – json serialization / deserialization
  
- **guava** – utility library for collections and concurrency
  
- **junit / androidx.test.junit / androidx.espresso** – unit and ui testing libraries
  
- **streaming & buffering techniques** – preloading upcoming tracks for seamless playback
  


