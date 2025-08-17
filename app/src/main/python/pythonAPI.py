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


seen_urls = set()

def _duration_to_millis(duration_str):
    """Преобразуем строку вида '3:45' в миллисекунды"""
    if not duration_str:
        return 0
    parts = duration_str.split(":")
    parts = [int(p) for p in parts]
    if len(parts) == 2:
        minutes, seconds = parts
        return (minutes * 60 + seconds) * 1000
    elif len(parts) == 3:
        hours, minutes, seconds = parts
        return (hours*3600 + minutes*60 + seconds) * 1000
    return 0

def extract_video_id(url_or_id: str) -> str:
    if not url_or_id:
        return ''
    m = re.search(r"v=([\w-]+)", url_or_id)
    if m:
        return m.group(1)
    m = re.search(r"youtu\.be/([\w-]+)", url_or_id)
    if m:
        return m.group(1)
    return url_or_id.strip()

def getRelatedTracks(track_id_or_url, max_results=25, official_only=True):
    def make_ytm_url(entity_id: str, entity_type: str = "artist") -> str:
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

    def normalize_browse_id(bid: str) -> str:
        """Приводим browseId/artist id к последнему сегменту (UC..., или id без префикса)."""
        if not bid:
            return ''
        # если у нас '/channel/UC...' или '/artist/...' — берем последний сегмент
        return bid.split('/')[-1]

    results = {}
    official_tracks = []
    seen_urls = set()

    try:
        video_id = extract_video_id(track_id_or_url)

        # --- получаем watch playlist (источник кандидатов) ---
        watch_playlist = _ytm.get_watch_playlist(video_id, limit=max_results * 3)
        tracks = watch_playlist.get('tracks', [])

        # --- пытаемся найти текущий трек в плейлисте чтобы узнать главного артиста ---
        main_artist_id = None
        main_artist_name = None
        for t in tracks:
            if t.get('videoId') == video_id:
                artists = t.get('artists', []) or []
                if artists:
                    # берем первого артиста как "главного"
                    main_artist_id = normalize_browse_id(artists[0].get('id') or '')
                    main_artist_name = artists[0].get('name', '')
                break

        # --- если нашли главного артиста, получаем related artists у него ---
        related_artists_info = []  # список словарей как в твоём примере
        related_ids_set = set()
        related_names_set = set()
        if main_artist_id:
            try:
                art = _ytm.get_artist(main_artist_id)
                related_data = art.get("related", {}).get("results", []) or []
                for item in related_data:
                    bid = item.get('browseId', '') or ''
                    name = item.get('title', '') or ''
                    thumb = (item.get("thumbnails") or [{}])[-1].get("url", "") if item.get("thumbnails") else ""
                    related_artists_info.append({
                        "name": name,
                        "url": f"https://music.youtube.com/channel/{bid}",
                        "image_url": thumb
                    })
                    if bid:
                        related_ids_set.add(normalize_browse_id(bid))
                    if name:
                        related_names_set.add(name.lower())
            except Exception:
                # если get_artist упал — просто продолжим без related
                related_artists_info = []
                related_ids_set = set()
                related_names_set = set()

        # --- подготовим два списка: приоритетные и обычные ---
        priority_buffer = []
        normal_buffer = []

        for track in tracks:
            vid = track.get('videoId')
            if not vid:
                continue

            track_url = f"https://music.youtube.com/watch?v={vid}"
            if track_url in seen_urls:
                continue
            seen_urls.add(track_url)

            # --- Title & duration ---
            title = track.get('title', '')
            duration_ms = int(track.get('lengthSeconds', 0)) * 1000 \
                          or track.get('durationMillis', 0) \
                          or _duration_to_millis(track.get('duration'))

            # --- Artists ---
            artists_info = [
                {'name': ar.get('name', ''), 'url': make_ytm_url(ar.get('id') or '')}
                for ar in track.get('artists', []) or []
            ]

            # --- Фильтр "официальные релизы" ---
            if official_only:
                if len(artists_info) == 1 and artists_info[0]['name'] == "YouTube":
                    continue

            # --- Image ---
            image_url = ''
            if track.get('thumbnail'):
                # некоторые структуры используют 'thumbnail', некоторые 'thumbnails'
                image_url = track['thumbnail'][-1].get('url', '')
            else:
                thumbs = track.get('thumbnails') or track.get('album', {}).get('thumbnails') or []
                if isinstance(thumbs, list) and thumbs:
                    image_url = thumbs[-1].get('url', '')

            # --- Album ---
            album_data = track.get('album', {})
            album_url = make_ytm_url(album_data.get('id') or '', "album")

            # --- собранный объект ---
            obj = {
                'id': vid or '',
                'title': title,
                'duration_ms': duration_ms,
                'url': track_url,
                'image_url': image_url,
                'album_url': album_url,
                'artists': artists_info
            }

            # --- определяем приоритетность: пересекается ли любой артист с related ---
            is_priority = False
            for ar in track.get('artists', []) or []:
                aid = normalize_browse_id(ar.get('id') or '')
                aname = (ar.get('name') or '').lower()
                if aid and aid in related_ids_set:
                    is_priority = True
                    break
                if aname and aname in related_names_set:
                    is_priority = True
                    break

            if is_priority:
                priority_buffer.append(obj)
            else:
                normal_buffer.append(obj)

            # если собрали достаточно — можем оптимально остановить раньше
            if len(priority_buffer) + len(normal_buffer) >= max_results * 3:
                # держим некоторый запас, затем обрежем до max_results
                break

        # --- финальная последовательность: сначала priority, затем normal ---
        final = priority_buffer + normal_buffer
        final = final[:max_results]

        results[track_id_or_url] = final

    except Exception as e:
        # сохраняем пустой результат (как раньше)
        results[track_id_or_url] = []

    return json.dumps({"results": results}, ensure_ascii=False)