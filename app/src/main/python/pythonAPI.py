import os
import re
import requests
import yt_dlp
from yt_dlp import YoutubeDL
from ytmusicapi import YTMusic
import json
import traceback
import random
import tempfile
import threading
import sys, io
import re
from typing import Optional
from typing import Tuple
import time
from difflib import SequenceMatcher
from typing import Any, Dict
from ytmusicapi.parsers.browsing import parse_related_artist
from ytmusicapi.setup import setup_browser
from ytmusicapi.navigation import nav

_ytm = YTMusic()

def getStream(youtube_url: str) -> str:
    ydl_opts = {
      'quiet': True,
      'skip_download': True,
      'format': 'bestaudio/best',
      'noplaylist': True,
      'extractor_args': {'youtube': {'player_client': ['web']}}
    }

    with YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(youtube_url, download=False)
        audio_url = info['url']
        return audio_url


def get_artist_id(item):
    # основной ID
    artist_id = item.get("browseId") or item.get("id") or item.get("channelId")

    # проверяем navigationEndpoint
    if not artist_id and "navigationEndpoint" in item:
        artist_id = item["navigationEndpoint"].get("browseId")

    # проверяем url / canonicalUrl
    if not artist_id:
        url = item.get("url") or item.get("canonicalUrl") or ""
        import re
        match = re.search(r'/channel/([A-Za-z0-9_-]+)', url)
        if match:
            artist_id = match.group(1)

    return artist_id or ""

# searchOnServer.py
def searchOnServer(q: str) -> str:
    q = q.strip()
    if not q:
        return json.dumps({"results": [], "error": "Empty query"})

    try:
        raw_results = _ytm.search(q, filter=None, limit=50)
    except Exception as e:
        return json.dumps({"results": [], "error": f"YTMusic API error: {e}"})

    results = []
    for item in raw_results:
        rtype = item.get("resultType")

        if rtype == "song":
            artists_list = item.get("artists") or []
            artist_str = ", ".join(a.get("name", "") for a in artists_list if a.get("name")) \
                         or item.get("artist", "") \
                         or item.get("channelName", "")
            album_url = None
            if isinstance(item.get("album"), dict):
                album_id = item["album"].get("id")
                if album_id:
                    album_url = f"https://music.youtube.com/browse/{album_id}"
            results.append({
                "type": "track",
                "id": item.get("videoId", ""),
                "name": item.get("title", ""),
                "artist": artist_str,
                "image_url": (item.get("thumbnails") or [{"url": ""}])[-1].get("url", ""),
                "album_url": album_url
            })

        elif rtype == "album":
            artist_str = item.get("artist") \
                or ", ".join(ar.get("name", "") for ar in item.get("artists", []) if ar.get("name")) \
                or item.get("subtitle", "")
            results.append({
                "type": "album",
                "id": item.get("browseId", ""),
                "name": item.get("title", ""),
                "artist": artist_str,
                "image_url": (item.get("thumbnails") or [{"url": ""}])[-1].get("url", "")
            })

        elif rtype == "artist":
            name = item.get("name") or item.get("title") or item.get("artist") or ""
            artist_id = get_artist_id(item)
            results.append({
                "type": "artist",
                "id": artist_id,
                "name": name,
                "image_url": (item.get("thumbnails") or [{"url": ""}])[-1].get("url", "")
            })

    return json.dumps({"results": results})


def getAlbum(ytmusic_url: str) -> str:
    def extract_id(url: str) -> str:
        m = re.search(r"(?:browse/|album/)([\w-]+)", url)
        return m.group(1) if m else url.strip()

    def duration_to_millis(dur: str) -> int:
        parts = [int(p) for p in dur.split(':')]
        if len(parts) == 3:
            h, m, s = parts
        else:
            h = 0; m, s = parts
        return (h*3600 + m*60 + s) * 1000

    def clean_album_name(name: str) -> str:
        # Убираем скобочные приписки типа (Deluxe Edition), [Remastered], {Expanded} и т.п.
        return re.sub(r"[\(\[\{].*?[\)\]\}]", "", name).strip()

    album_id = extract_id(ytmusic_url)
    album = _ytm.get_album(album_id)
    if not album:
        return json.dumps({
            "album":     "",
            "artist":    [],
            "year":      "",
            "image_url": "",
            "tracks":    []
        }, ensure_ascii=False)

    album_title = album.get('title', '')
    album_title_clean = clean_album_name(album_title)
    tracks = []
    used_video_ids = set()

    for item in album.get('tracks', []):
        orig_vid = item.get('videoId')
        if not orig_vid:
            continue

        orig_duration_ms = duration_to_millis(item.get('duration', '0:00'))
        vid = orig_vid
        title = item.get('title', '')

        # Собираем всех артистов трека
        artists_raw = item.get('artists', [])
        artists_info = []
        for ar in artists_raw:
            name = ar.get('name', '')
            ar_id = ar.get('id')
            url = f"https://music.youtube.com/channel/{ar_id}" if ar_id else ''
            artists_info.append({'name': name, 'url': url})

        # Для поиска используем имя первого артиста
        first_artist = artists_info[0]['name'] if artists_info else ''

        vt = item.get('videoType', '')
        if vt in ('MUSIC_VIDEO_TYPE_OMV', 'MUSIC_VIDEO_TYPE_UGC'):
            query = f"{title} - {first_artist}"
            results = _ytm.search(query, filter='songs', limit=5)

            for res in results:
                alb = res.get('album') or {}
                alb_name_clean = clean_album_name(alb.get('name', ''))
                if alb.get('id') == album_id or alb_name_clean == album_title_clean:
                    candidate_vid = res.get('videoId', orig_vid)
                    if candidate_vid in used_video_ids:
                        continue
                    res_dur = res.get('duration')
                    if res_dur:
                        cand_duration_ms = duration_to_millis(res_dur)
                        if cand_duration_ms != orig_duration_ms:
                            continue
                    vid = candidate_vid
                    break

        if vid in used_video_ids:
            vid = orig_vid
        used_video_ids.add(vid)

        tracks.append({
            'id': vid,
            'title': title,
            'track_num': item.get('trackNumber', 0),
            'url': f"https://music.youtube.com/watch?v={vid}",
            'duration_ms': orig_duration_ms,
            'artists': artists_info
        })

    album_artist_name = album.get('artist', '').lower()

    if True :
        # Собираем уникальных артистов из всех треков
        unique_artists = {}
        for track in tracks:
            for ar in track['artists']:
                key = ar['url'] or ar['name']  # уникальный ключ
                if key not in unique_artists:
                    unique_artists[key] = ar
        artists_all = list(unique_artists.values())
    else:
        # Просто берём артистов альбома из метаданных
        artists_all = []
        for ar in album.get('artists', []):
            name = ar.get('name', '')
            ar_id = ar.get('id')
            url = f"https://music.youtube.com/channel/{ar_id}" if ar_id else ''
            artists_all.append({'name': name, 'url': url})

    result = {
        'album': album_title,
        'artist': artists_all,
        'year': str(album.get('year', ''))[:4],
        'image_url': (album.get('thumbnails') or [{}])[-1].get('url', ''),
        'tracks': tracks
    }

    return json.dumps(result, ensure_ascii=False, default=str)

def getTrack(track_url: str) -> str:
    def duration_to_millis(dur: str) -> int:
        parts = [int(p) for p in dur.split(':')]
        if len(parts) == 3:
            h, m, s = parts
        else:
            h = 0; m, s = parts
        return (h * 3600 + m * 60 + s) * 1000

    def make_ytm_url(entity_id: str, entity_type: str = "artist") -> str:
        """
        Преобразует ID из YTMusic API в полный URL.
        entity_type: 'artist' или 'album'
        """
        if not entity_id:
            return ''

        if entity_type == "album":
            # Альбомы почти всегда имеют вид /browse/MPREb_xxx
            if entity_id.startswith('/browse/'):
                return f"https://music.youtube.com{entity_id}"
            return f"https://music.youtube.com/browse/{entity_id}"
        else:
            # Артисты
            if entity_id.startswith('/channel/') or entity_id.startswith('/browse/') or entity_id.startswith('/artist/'):
                return f"https://music.youtube.com{entity_id}"
            return f"https://music.youtube.com/channel/{entity_id}"

    video_id_match = re.search(r"v=([\w-]+)", track_url)
    video_id = video_id_match.group(1) if video_id_match else track_url.strip()

    watch_playlist = _ytm.get_watch_playlist(video_id)
    if not watch_playlist or not watch_playlist.get('tracks'):
        return json.dumps({
            "title": "",
            "artists": [],
            "duration_ms": 0,
            "image_url": "",
            "url": track_url,
            "album_url": ""
        })

    track = watch_playlist['tracks'][0]
    title = track.get('title', '')
    duration_ms = int(track.get('lengthSeconds', 0)) * 1000

    # Артисты
    artists_info = [
        {'name': ar.get('name', ''), 'url': make_ytm_url(ar.get('id') or '')}
        for ar in track.get('artists', [])
    ]

    # Обложка
    image_url = track.get('thumbnail', [{}])[-1].get('url', '') if track.get('thumbnail') else ''

    # Альбом
    album_data = track.get('album', {})
    album_url = make_ytm_url(album_data.get('id') or '', "album")

    result = {
        'title': title,
        'artists': artists_info,
        'duration_ms': duration_ms,
        'image_url': image_url,
        'url': track_url,
        'album_url': album_url
    }
    return json.dumps(result, ensure_ascii=False)


def getTrackImage(track_url: str) -> str:

    def make_ytm_url(entity_id: str, entity_type: str = "artist") -> str:
        """
        Преобразует ID из YTMusic API в полный URL.
        entity_type: 'artist' или 'album'
        """
        if not entity_id:
            return ''

        if entity_type == "album":
            # Альбомы почти всегда имеют вид /browse/MPREb_xxx
            if entity_id.startswith('/browse/'):
                return f"https://music.youtube.com{entity_id}"
            return f"https://music.youtube.com/browse/{entity_id}"
        else:
            # Артисты
            if entity_id.startswith('/channel/') or entity_id.startswith('/browse/') or entity_id.startswith('/artist/'):
                return f"https://music.youtube.com{entity_id}"
            return f"https://music.youtube.com/channel/{entity_id}"

    video_id_match = re.search(r"v=([\w-]+)", track_url)
    video_id = video_id_match.group(1) if video_id_match else track_url.strip()

    watch_playlist = _ytm.get_watch_playlist(video_id)
    if not watch_playlist or not watch_playlist.get('tracks'):
        return json.dumps({
            "title": "",
            "artists": [],
            "duration_ms": 0,
            "image_url": "",
            "url": track_url,
            "album_url": ""
        })

    track = watch_playlist['tracks'][0]

    # Обложка
    image_url = track.get('thumbnail', [{}])[-1].get('url', '') if track.get('thumbnail') else ''

    return image_url



def getArtist(ytmusic_url: str) -> str:
    """
    Возвращает JSON-строку с метаданными артиста:
    - имя, ссылка, обложка
    - полный список альбомов и синглов (с помощью continuation, если нужно)
    - топ-5 треков
    """
    try:
        # Извлекаем artist_id из URL
        match = re.search(r"(channel/|browse/)?(UC[\w-]+)", ytmusic_url)
        artist_id = match.group(2) if match else ytmusic_url.strip()
        art = _ytm.get_artist(artist_id)
        if not art:
            return "{}"

        image_url = (art.get("thumbnails") or [{"url": ""}])[-1].get("url", "")

        albums = []
        seen = set()

        def fetch_albums(browse_id: str, params: str, is_single_section: bool):
            """Выкачивает и добавляет в albums все итемы по одному browseId+params."""
            try:
                items = _ytm.get_artist_albums(browse_id, params, limit=None)
            except Exception:
                return
            for item in items:
                sbid = item.get("browseId")
                if not sbid or sbid in seen:
                    continue
                seen.add(sbid)
                year = (item.get("year") or "")[:4]
                albums.append({
                    "id": sbid,
                    "name": item.get("title", ""),
                    "image_url": (item.get("thumbnails") or [{}])[0].get("url", ""),
                    "url": f"https://music.youtube.com/browse/{sbid}",
                    "is_single": is_single_section,
                    "year": year
                })

        # 1) Пробуем стандартные поля albums/singles
        for key in ("albums", "singles", "albumReleases", "singleReleases"):
            sec = art.get(key, {})
            bid = sec.get("browseId")
            params = sec.get("params")
            if bid and params:
                is_single_section = "single" in key.lower()
                fetch_albums(bid, params, is_single_section)

        # 2) Если всё ещё пусто, ищем в секциях (e.g., «Top releases», «Albums»)
        if not albums:
            for section in art.get("sections", []):
                title = section.get("title", "")
                if title.lower() not in ["albums", "singles", "album releases", "single releases", "top releases", "discography"]:
                    continue  # Только релевантные секции

                is_single_section = "single" in title.lower()

                # Добавляем initial contents
                contents = section.get("contents", [])
                for item in contents:
                    sbid = item.get("browseId")
                    if not sbid or sbid in seen:
                        continue
                    seen.add(sbid)
                    year = (item.get("year") or item.get("subtitle") or "")[-4:]  # Иногда year в subtitle
                    albums.append({
                        "id": sbid,
                        "name": item.get("title", ""),
                        "image_url": (item.get("thumbnails") or [{}])[0].get("url", ""),
                        "url": f"https://music.youtube.com/browse/{sbid}",
                        "is_single": is_single_section,
                        "year": year
                    })

                # Обрабатываем continuation, если есть
                cont = section.get("continuations") or []
                if cont:
                    token = cont[0].get("nextContinuationData", {}).get("continuation") or cont[0].get("nextEndpoint", {}).get("params")
                    if token:
                        # Фетчим все continuations
                        more_items = fetch_continuations(token)
                        for item in more_items:
                            sbid = item.get("browseId")
                            if not sbid or sbid in seen:
                                continue
                            seen.add(sbid)
                            year = (item.get("year") or item.get("subtitle") or "")[-4:]
                            albums.append({
                                "id": sbid,
                                "name": item.get("title", ""),
                                "image_url": (item.get("thumbnails") or [{}])[0].get("url", ""),
                                "url": f"https://music.youtube.com/browse/{sbid}",
                                "is_single": is_single_section,
                                "year": year
                            })

        # Топ-5 треков
        top_tracks = []
        for t in art.get("songs", {}).get("results", [])[:5]:
            # Конвертация длительности в миллисекунды
            def duration_to_millis(dur: str) -> int:
                parts = [int(p) for p in dur.split(':')]
                if len(parts) == 3:
                    h, m, s = parts
                else:
                    h = 0; m, s = parts
                return (h*3600 + m*60 + s) * 1000

            duration_ms = duration_to_millis(t.get("duration", "0:00"))

            # Формируем массив артистов
            artists_raw = t.get('artists', [])
            artists_info = []
            for ar in artists_raw:
                name = ar.get('name', '')
                ar_id = ar.get('id') or ''
                url = ''
                if ar_id:
                    if ar_id.startswith('/channel/') or ar_id.startswith('/browse/') or ar_id.startswith('/artist/'):
                        url = f"https://music.youtube.com{ar_id}"
                    else:
                        url = f"https://music.youtube.com/channel/{ar_id}"
                artists_info.append({'name': name, 'url': url})

            # Оригинальный альбом
            album_data = t.get("album", {})
            album_id = album_data.get("id") or ''
            album_url = ''
            if album_id:
                album_url = f"https://music.youtube.com/browse/{album_id}"

            top_tracks.append({
                "id": t.get("videoId", ""),
                "name": t.get("title", ""),
                "album": album_data.get("name", ""),
                "album_image_url": (t.get("thumbnails") or [{}])[0].get("url", ""),
                "preview_url": None,
                "url": f"https://music.youtube.com/watch?v={t.get('videoId','')}",
                "popularity": 0,
                "duration_ms": duration_ms,
                "artists": artists_info,
                "album_url": album_url
            })

        # Related artists
        related = []
        related_data = art.get("related", {}).get("results", [])
        for item in related_data:
            related.append({
                "name": item.get("title", ""),
                "url": f"https://music.youtube.com/channel/{item.get('browseId', '')}",
                "image_url": (item.get("thumbnails") or [{}])[-1].get("url", "")
            })

        result = {
            "type": "artist",
            "artist": art.get("name", ""),
            "related_artists": related,
            "link": f"https://music.youtube.com/channel/{artist_id}",
            "image_url": image_url,
            "monthly_listeners": art.get("stats", {}).get("subscriberCount", 0),
            "albums": albums,
            "top_tracks": top_tracks
        }
        return json.dumps(result, ensure_ascii=False, indent=2)

    except Exception:
        traceback.print_exc()
        return "{}"


def fetch_continuations(token: str) -> list:
    """Custom функция для фетча всех continuation items (предполагаем carousel для releases)."""
    items = []
    renderer = 'musicCarouselShelfContinuation'  # Или 'musicShelfContinuation', если секция shelf
    while token:
        body = {"context": _ytm.context, "continuation": token}
        try:
            response = _ytm._send_request("browse", body)
            cont_contents = nav(response, ['continuationContents', renderer, 'contents'], true_req=True)
            items.extend(cont_contents)
            token = nav(response, ['continuationContents', renderer, 'continuations', 0, 'nextContinuationData', 'continuation'], true_req=False)
        except Exception:
            break  # Если ошибка (e.g., неправильный renderer), остановиться
    return items


def getRelatedForMany(track_urls, max_results_per = 25):
    """
    Принимает строку (video url/id) или список строк.
    Возвращает JSON со словарём "results": { original_url: [tracks...] }
    и опционально "errors": { original_url: "error message" }.
    Каждая запись track — в той же структуре, что и getTrack возвращает:
    { title, artists: [{name,url}], duration_ms, image_url, url, album_url }
    """
    # Helpers (локальные, чтобы не ломать глобальный namespace)
    def _extract_video_id(track_url: str) -> str:
        if not track_url:
            return ''
        m = re.search(r"v=([\w-]+)", track_url)
        if m:
            return m.group(1)
        m = re.search(r"youtu\.be/([\w-]+)", track_url)
        if m:
            return m.group(1)
        return track_url.strip()

    def _duration_to_millis(dur: Any) -> int:
        if dur is None:
            return 0
        s = str(dur).strip()
        if s.isdigit():
            return int(s) * 1000
        parts = [int(p) for p in s.split(':') if p != '']
        if len(parts) == 3:
            h, m, sec = parts
        elif len(parts) == 2:
            h = 0; m, sec = parts
        elif len(parts) == 1:
            h = 0; m = 0; sec = parts[0]
        else:
            return 0
        return (h*3600 + m*60 + sec) * 1000

    def _make_ytm_url(entity_id: str, entity_type: str = "artist") -> str:
        if not entity_id:
            return ''
        if entity_type == "album":
            if entity_id.startswith('/browse/'):
                return f"https://music.youtube.com{entity_id}"
            return f"https://music.youtube.com/browse/{entity_id}"
        else:
            if entity_id.startswith('/channel/') or entity_id.startswith('/browse/') or entity_id.startswith('/artist/'):
                return f"https://music.youtube.com{entity_id}"
            return f"https://music.youtube.com/channel/{entity_id}"

    def _find_related_browse_id_from_watch(watch: dict) -> str:
        if not watch:
            return ''
        for k in ('related', 'relatedBrowseId', 'related_browse_id', 'browseId'):
            if k in watch and watch[k]:
                return watch[k]
        # рекурсивный поиск похожих строк
        def walk(o):
            if isinstance(o, dict):
                for _, v in o.items():
                    if isinstance(v, str):
                        if v.startswith('/browse/') or v.startswith('FE') or v.startswith('RD'):
                            return v
                    elif isinstance(v, (dict, list)):
                        f = walk(v)
                        if f:
                            return f
            elif isinstance(o, list):
                for i in o:
                    f = walk(i)
                    if f:
                        return f
            return None
        found = walk(watch)
        return found or ''

    def _normalize_item(item: dict, original_url: str) -> dict:
        # video id
        video_id = item.get('videoId') or item.get('id') or item.get('video_id') or ''
        # title
        title = ''
        if isinstance(item.get('title'), dict):
            title = item['title'].get('runs', [{}])[0].get('text', '') if item.get('title') else ''
        else:
            title = item.get('title') or item.get('name') or item.get('titleText') or ''

        # duration
        duration_ms = 0
        if item.get('lengthSeconds'):
            try:
                duration_ms = int(item.get('lengthSeconds')) * 1000
            except:
                duration_ms = _duration_to_millis(item.get('lengthSeconds'))
        elif item.get('duration'):
            duration_ms = _duration_to_millis(item.get('duration'))
        elif item.get('length'):
            duration_ms = _duration_to_millis(item.get('length'))

        # artists
        artists_info = []
        artists_field = item.get('artists') or item.get('artist') or item.get('artistsText') or []
        if isinstance(artists_field, list):
            for ar in artists_field:
                if isinstance(ar, dict):
                    name = ar.get('name') or ar.get('artistName') or ar.get('title') or ''
                    aid = ar.get('id') or ar.get('browseId') or ''
                    artists_info.append({'name': name, 'url': _make_ytm_url(aid)})
                else:
                    artists_info.append({'name': str(ar), 'url': ''})
        else:
            if isinstance(artists_field, str):
                for name in [n.strip() for n in re.split(r'[•,;/]', artists_field) if n.strip()]:
                    artists_info.append({'name': name, 'url': ''})

        # image
        image_url = ''
        thumbs = item.get('thumbnail') or item.get('thumbnails') or item.get('thumbnailText') or []
        if isinstance(thumbs, list) and thumbs:
            last = thumbs[-1]
            if isinstance(last, dict):
                image_url = last.get('url') or last.get('thumbnails', [{}])[-1].get('url', '')
        elif isinstance(thumbs, dict):
            image_url = thumbs.get('url', '')
        else:
            maybe = item.get('thumbnailText') or {}
            if isinstance(maybe, dict):
                image_url = maybe.get('thumbnails', [{}])[-1].get('url', '')

        album = item.get('album') or {}
        album_id = album.get('id') or album.get('browseId') or ''
        album_url = _make_ytm_url(album_id, "album")

        url = f"https://music.youtube.com/watch?v={video_id}" if video_id else original_url

        return {
            'title': title or '',
            'artists': artists_info,
            'duration_ms': int(duration_ms or 0),
            'image_url': image_url or '',
            'url': url,
            'album_url': album_url
        }

    # ---- main logic ----
    single_input = False
    if isinstance(track_urls, (str,)):
        single_input = True
        track_list = [track_urls]
    elif isinstance(track_urls, (list, tuple)):
        track_list = list(track_urls)
    else:
        # unsupported type
        return json.dumps({"results": {}, "errors": {"_input": "unsupported input type"}}, ensure_ascii=False)

    results = {}
    errors = {}

    for orig in track_list:
        try:
            vid = _extract_video_id(orig)
            if not vid:
                errors[orig] = "empty video id"
                results[orig] = []
                continue

            # 1) try watch playlist → find browseId
            try:
                watch = _ytm.get_watch_playlist(vid)
            except Exception as e:
                watch = None

            browse_id = _find_related_browse_id_from_watch(watch) if watch else ''

            related_items = []
            seen_urls = set()

            # 2) if browse_id present => try get_song_related(browse_id)
            if browse_id:
                try:
                    related_sections = _ytm.get_song_related(browse_id)
                except TypeError:
                    # some versions accept pos arg
                    try:
                        related_sections = _ytm.get_song_related(browse_id)
                    except Exception:
                        related_sections = None
                except Exception:
                    related_sections = None

                if related_sections:
                    # normalize different shapes of response
                    def walk_sections(rs):
                        items = []
                        if isinstance(rs, dict):
                            for k in ('contents', 'results', 'items'):
                                if k in rs and isinstance(rs[k], list):
                                    items.extend(rs[k])
                            for v in rs.values():
                                if isinstance(v, list):
                                    for it in v:
                                        if isinstance(it, dict) and ('videoId' in it or 'id' in it):
                                            items.append(it)
                        elif isinstance(rs, list):
                            for section in rs:
                                if isinstance(section, dict):
                                    if 'contents' in section and isinstance(section['contents'], list):
                                        items.extend(section['contents'])
                                    elif 'results' in section and isinstance(section['results'], list):
                                        items.extend(section['results'])
                                    else:
                                        for v in section.values():
                                            if isinstance(v, list):
                                                for it in v:
                                                    if isinstance(it, dict) and ('videoId' in it or 'id' in it):
                                                        items.append(it)
                        return items

                    raw = walk_sections(related_sections)
                    for it in raw:
                        if len(related_items) >= max_results_per:
                            break
                        normalized = _normalize_item(it, orig)
                        if normalized['url'] and normalized['url'] not in seen_urls:
                            related_items.append(normalized)
                            seen_urls.add(normalized['url'])

            # 3) fallback to watch['tracks'] if nothing found
            if not related_items and watch:
                tracks = watch.get('tracks') or []
                for it in tracks:
                    if len(related_items) >= max_results_per:
                        break
                    normalized = _normalize_item(it, orig)
                    if normalized['url'] not in seen_urls:
                        related_items.append(normalized)
                        seen_urls.add(normalized['url'])

            # 4) final fallback - try get_song_related by video id directly (some versions)
            if not related_items:
                try:
                    possible = _ytm.get_song_related(vid)
                    if possible:
                        rawlist = possible if isinstance(possible, list) else [possible]
                        for sect in rawlist:
                            contents = sect.get('contents') if isinstance(sect, dict) else []
                            for it in contents:
                                if len(related_items) >= max_results_per:
                                    break
                                normalized = _normalize_item(it, orig)
                                if normalized['url'] not in seen_urls:
                                    related_items.append(normalized)
                                    seen_urls.add(normalized['url'])
                except Exception:
                    pass

            results[orig] = related_items[:max_results_per]

        except Exception as e:
            errors[orig] = f"exception: {repr(e)}\n{traceback.format_exc()}"
            results[orig] = []

    out = {"results": results}
    if errors:
        out["errors"] = errors
    return json.dumps(out, ensure_ascii=False)