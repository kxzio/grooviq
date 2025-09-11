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
import concurrent.futures
import threading
import json
from typing import Union, List
from concurrent.futures import ThreadPoolExecutor, as_completed

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

    # --- 🔎 доп.обработка артистов с пустым именем в топ-3 ---
    for idx, res in enumerate(results[:3]):
        if res["type"] == "artist" and not res["name"].strip():
            try:
                artist_candidates = _ytm.search(q, filter="artists", limit=10)
                for cand in artist_candidates:
                    cand_img = (cand.get("thumbnails") or [{"url": ""}])[-1].get("url", "")
                    if cand_img and cand_img == res["image_url"]:
                        fixed_name = cand.get("artist") or cand.get("name") or cand.get("title") or ""
                        fixed_id = cand.get("browseId") or cand.get("artistId") or ""
                        if fixed_name:
                            res["name"] = fixed_name
                        if fixed_id:
                            res["id"] = fixed_id
                        break
            except Exception:
                pass

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

def getRelatedTracks(track_ids_or_urls: Union[str, List[str]], max_results=20, official_only=True):
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
        return bid.split('/')[-1]

    def process_single(track_id_or_url: str):
        results = {}
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
                        main_artist_id = normalize_browse_id(artists[0].get('id') or '')
                        main_artist_name = artists[0].get('name', '')
                    break

            # --- related artists ---
            related_ids_set = set()
            related_names_set = set()
            if main_artist_id:
                try:
                    art = _ytm.get_artist(main_artist_id)
                    related_data = art.get("related", {}).get("results", []) or []
                    for item in related_data:
                        bid = item.get('browseId', '') or ''
                        name = item.get('title', '') or ''
                        if bid:
                            related_ids_set.add(normalize_browse_id(bid))
                        if name:
                            related_names_set.add(name.lower())
                except Exception:
                    related_ids_set = set()
                    related_names_set = set()

            # --- подготовим два списка ---
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

                title = track.get('title', '')
                duration_ms = int(track.get('lengthSeconds', 0)) * 1000 \
                              or track.get('durationMillis', 0) \
                              or _duration_to_millis(track.get('duration'))

                artists_info = [
                    {'name': ar.get('name', ''), 'url': make_ytm_url(ar.get('id') or '')}
                    for ar in track.get('artists', []) or []
                ]

                if official_only:
                    if len(artists_info) == 1 and artists_info[0]['name'] == "YouTube":
                        continue

                image_url = ''
                if track.get('thumbnail'):
                    image_url = track['thumbnail'][-1].get('url', '')
                else:
                    thumbs = track.get('thumbnails') or track.get('album', {}).get('thumbnails') or []
                    if isinstance(thumbs, list) and thumbs:
                        image_url = thumbs[-1].get('url', '')

                album_data = track.get('album', {})
                album_url = make_ytm_url(album_data.get('id') or '', "album")

                obj = {
                    'id': vid or '',
                    'title': title,
                    'duration_ms': duration_ms,
                    'url': track_url,
                    'image_url': image_url,
                    'album_url': album_url,
                    'artists': artists_info
                }

                # --- приоритет ---
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

                if len(priority_buffer) + len(normal_buffer) >= max_results * 3:
                    break

            final = priority_buffer + normal_buffer
            final = final[:max_results]

            results[track_id_or_url] = final
        except Exception:
            results[track_id_or_url] = []

        return results

    # --- главный блок ---
    if isinstance(track_ids_or_urls, str):
        track_ids = [track_ids_or_urls]
    else:
        track_ids = track_ids_or_urls

    all_results = {}
    with ThreadPoolExecutor(max_workers=min(8, len(track_ids))) as executor:
        futures = {executor.submit(process_single, tid): tid for tid in track_ids}
        for future in as_completed(futures):
            result = future.result()
            all_results.update(result)

    return json.dumps({"results": all_results}, ensure_ascii=False)

def replaceSongs(json_list, official_only: bool = True,
                      per_artist_limit: int = 10,
                      max_workers: int = 6,
                      album_pairs_limit: int = 3,
                      tracks_per_album: int = 5):
    """
    Быстрая замена песен, сохраняющая артистов.
    См. описание в обсуждении — возвращает json {"results": { input_url: [track_obj], ... }}
    Параметры можно уменьшать для ускорения.
    """
    try:
        song_urls = json.loads(json_list)
    except Exception:
        song_urls = []

    # Быстрые проверки
    if not song_urls:
        return json.dumps({"results": {}}, ensure_ascii=False)

    # ---- вспомогательные функции ----
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
        if not bid:
            return ''
        return bid.split('/')[-1]

    def normalize_track_obj(raw):
        """Быстрая нормализация в требуемый формат или None."""
        if not raw or not isinstance(raw, dict):
            return None
        vid = raw.get('videoId') or raw.get('id') or (raw.get('video') or {}).get('videoId') or ''
        if not vid:
            return None
        title = raw.get('title') or raw.get('name') or (raw.get('video') or {}).get('title') or ''
        duration_ms = 0
        try:
            if raw.get('lengthSeconds'):
                duration_ms = int(raw.get('lengthSeconds')) * 1000
            else:
                duration_ms = raw.get('durationMillis') or 0
        except Exception:
            duration_ms = raw.get('durationMillis') or 0
        artists_raw = raw.get('artists') or (raw.get('video') or {}).get('artists') or []
        artists_info = []
        for ar in artists_raw:
            aname = ar.get('name') or ''
            aid = ar.get('id') or ''
            url = ''
            if aid:
                if aid.startswith('/channel/') or aid.startswith('/browse/') or aid.startswith('/artist/'):
                    url = f"https://music.youtube.com{aid}"
                else:
                    url = f"https://music.youtube.com/channel/{aid}"
            artists_info.append({'name': aname, 'url': url})
        image_url = ''
        if raw.get('thumbnail'):
            try:
                image_url = raw['thumbnail'][-1].get('url','')
            except Exception:
                image_url = ''
        else:
            thumbs = raw.get('thumbnails') or raw.get('album', {}).get('thumbnails') or []
            if isinstance(thumbs, list) and thumbs:
                image_url = thumbs[-1].get('url','')
        album_url = ''
        alb = raw.get('album') or {}
        if alb:
            aid = alb.get('id') or ''
            if aid:
                album_url = make_ytm_url(aid, "album")
        return {
            'id': vid,
            'title': title,
            'duration_ms': duration_ms or 0,
            'url': f"https://music.youtube.com/watch?v={vid}",
            'image_url': image_url,
            'album_url': album_url,
            'artists': artists_info
        }

    # ---- кэши и синхронизация (потоки будут писать в кэш) ----
    artist_cache = {}
    album_cache = {}
    cache_lock = threading.Lock()

    # Собираем входные id и оригинальные объекты (попытка получить метаданные одной сетью)
    input_ids = set()
    original_objs = {}
    input_vids = []  # сохранить порядок входа
    for inp in song_urls:
        try:
            vid = extract_video_id(inp)
        except Exception:
            vid = inp
        input_vids.append((inp, vid))
    # Получаем metadata для каждого vid (можно делать параллельно, но обычно get_watch_playlist один за другим ок)
    for inp, vid in input_vids:
        orig_obj = None
        try:
            wp = _ytm.get_watch_playlist(vid, limit=1) or {}
            tracks = wp.get('tracks', []) or []
            found = None
            for t in tracks:
                if t.get('videoId') == vid or t.get('id') == vid:
                    found = t
                    break
            if not found and tracks:
                found = tracks[0]
            if found:
                norm = normalize_track_obj(found)
                if norm:
                    orig_obj = norm
        except Exception:
            orig_obj = None
        if not orig_obj:
            orig_obj = {
                'id': vid,
                'title': '',
                'duration_ms': 0,
                'url': f'https://music.youtube.com/watch?v={vid}',
                'image_url': '',
                'album_url': '',
                'artists': []
            }
        input_ids.add(orig_obj.get('id'))
        original_objs[inp] = orig_obj

    # --- Определяем primary artist для каждого входа и группируем ---
    artist_to_inputs = {}   # artist_id -> list of input keys
    input_primary_artist = {}  # input -> artist_id (or None)
    for inp in song_urls:
        orig = original_objs.get(inp, {})
        primary_artist_id = None
        if orig.get('artists'):
            first = orig['artists'][0]
            aurl = first.get('url') or ''
            if aurl:
                primary_artist_id = normalize_browse_id(aurl.split('/')[-1])
        # дополнительная попытка — если нет
        if not primary_artist_id:
            vid = orig.get('id')
            if vid:
                try:
                    wp = _ytm.get_watch_playlist(vid, limit=1) or {}
                    tracks = wp.get('tracks', []) or []
                    for t in tracks:
                        if t.get('videoId') == vid or t.get('id') == vid:
                            artists = t.get('artists') or []
                            if artists:
                                primary_artist_id = normalize_browse_id(artists[0].get('id') or '')
                            break
                except Exception:
                    primary_artist_id = None
        input_primary_artist[inp] = primary_artist_id
        if primary_artist_id:
            artist_to_inputs.setdefault(primary_artist_id, []).append(inp)
        else:
            artist_to_inputs.setdefault(None, []).append(inp)

    # ---- Функция, собирающая кандидатов для артиста (будет выполняться в пуле) ----
    def gather_for_artist(aid):
        """Возвращает список normalized candidates для артиста aid (не включая exclude_ids)."""
        candidates = []
        if not aid:
            return candidates
        # кэш lookup
        with cache_lock:
            if aid in artist_cache:
                art_page = artist_cache[aid]
            else:
                try:
                    art_page = _ytm.get_artist(aid)
                except Exception:
                    art_page = {}
                artist_cache[aid] = art_page

        # 1) songs/tracks/top
        songs_block = art_page.get('songs') or art_page.get('tracks') or {}
        if isinstance(songs_block, dict):
            songs_list = songs_block.get('results') or []
        elif isinstance(songs_block, list):
            songs_list = songs_block
        else:
            songs_list = []
        for s in songs_list:
            norm = normalize_track_obj(s)
            if not norm:
                continue
            # фильтр оффишн
            if official_only and len(norm['artists']) == 1 and norm['artists'][0]['name'] == "YouTube":
                continue
            candidates.append(norm)
            if len(candidates) >= per_artist_limit:
                return candidates

        # 2) если мало -> пробуем несколько первых альбомов (ограниченно)
        alb_pairs = []
        for key in ("albums", "singles", "albumReleases", "singleReleases"):
            sec = art_page.get(key, {})
            bid = sec.get("browseId")
            params = sec.get("params")
            if bid and params:
                alb_pairs.append((normalize_browse_id(bid), params))
        if not alb_pairs:
            for section in art_page.get('sections', []) or []:
                title = (section.get('title') or "").lower()
                if "album" in title or "single" in title or "release" in title or "discography" in title:
                    bid = section.get("browseId") or section.get("endpoint", {}).get("browseId") or ''
                    params = None
                    cont = section.get("continuations") or []
                    if cont:
                        params = cont[0].get("nextContinuationData", {}).get("continuation") or cont[0].get("nextEndpoint", {}).get("params")
                    if bid:
                        alb_pairs.append((normalize_browse_id(bid), params))

        tried = 0
        for (bid, params) in alb_pairs:
            if tried >= album_pairs_limit or len(candidates) >= per_artist_limit:
                break
            tried += 1
            # если есть params — используем get_artist_albums
            try:
                items = []
                if params:
                    items = _ytm.get_artist_albums(bid, params, limit=3) or []
                else:
                    # если bid выглядит как album id — попробуем get_album
                    try:
                        album_page = None
                        s_norm = normalize_browse_id(bid)
                        with cache_lock:
                            album_page = album_cache.get(s_norm)
                        if album_page is None:
                            try:
                                album_page = _ytm.get_album(s_norm)
                            except Exception:
                                album_page = {}
                            with cache_lock:
                                album_cache[s_norm] = album_page
                        items = [{'browseId': s_norm}]  # маркер что нужно взять этот альбом
                    except Exception:
                        items = []
                for it in items:
                    sbid = it.get('browseId') or it.get('id') or ''
                    if not sbid:
                        continue
                    s_norm = normalize_browse_id(sbid)
                    # получить album_page (с кэшем)
                    with cache_lock:
                        album_page = album_cache.get(s_norm)
                    if album_page is None:
                        try:
                            album_page = _ytm.get_album(s_norm)
                        except Exception:
                            album_page = {}
                        with cache_lock:
                            album_cache[s_norm] = album_page
                    for tr in (album_page.get('tracks') or [])[:tracks_per_album]:
                        norm = normalize_track_obj(tr)
                        if not norm:
                            continue
                        if official_only and len(norm['artists']) == 1 and norm['artists'][0]['name'] == "YouTube":
                            continue
                        candidates.append(norm)
                        if len(candidates) >= per_artist_limit:
                            break
                    if len(candidates) >= per_artist_limit:
                        break
            except Exception:
                continue

        # Deduplicate by id preserving order
        seen = set()
        dedup = []
        for c in candidates:
            cid = c.get('id')
            if cid and cid not in seen:
                seen.add(cid)
                dedup.append(c)
        return dedup[:per_artist_limit]

    # ---- Параллельно собираем кандидатов для всех уникальных артистов ----
    unique_artists = [aid for aid in artist_to_inputs.keys() if aid]
    artist_candidates = {}  # aid -> list of candidates
    if unique_artists:
        with concurrent.futures.ThreadPoolExecutor(max_workers=min(max_workers, max(1, len(unique_artists)))) as ex:
            future_to_aid = { ex.submit(gather_for_artist, aid): aid for aid in unique_artists }
            for fut in concurrent.futures.as_completed(future_to_aid):
                aid = future_to_aid[fut]
                try:
                    artist_candidates[aid] = fut.result() or []
                except Exception:
                    artist_candidates[aid] = []

    # ---- Теперь распределяем кандидатов по входам ----
    results = {}
    # Чтобы избежать повторов в ответах, будем пытаться выдавать уникальные кандидаты по артисту
    for aid, inputs_for_artist in artist_to_inputs.items():
        if not aid:
            # для None — просто возвращаем оригинал
            for inp in inputs_for_artist:
                results[inp] = [ original_objs.get(inp) ]
            continue
        pool = artist_candidates.get(aid, [])
        # Shuffle for randomness
        random.shuffle(pool)
        # если пул меньше количества входов, мы позволим повторы, но сначала выдадим уникальные
        used_ids = set()
        idx = 0
        for inp in inputs_for_artist:
            orig = original_objs.get(inp, {})
            orig_id = orig.get('id')
            # найдем кандидат, не совпадающий с входными id (input_ids) и не равный оригиналу
            chosen = None
            while idx < len(pool):
                cand = pool[idx]
                idx += 1
                if not cand:
                    continue
                cid = cand.get('id')
                if not cid:
                    continue
                if cid == orig_id:
                    continue
                if cid in input_ids:
                    # исключаем исходники из всей группы
                    continue
                if cid in used_ids:
                    continue
                # OK
                chosen = cand
                used_ids.add(cid)
                break
            # если не нашли уникального — попробуем взять любой, кроме orig_id
            if not chosen:
                for cand in pool:
                    cid = cand.get('id')
                    if cid and cid != orig_id and cid not in used_ids and cid not in input_ids:
                        chosen = cand
                        used_ids.add(cid)
                        break
            # финальный fallback — оригинал
            if not chosen:
                chosen = orig
            results[inp] = [chosen]

    # ---- Вернём JSON ----
    return json.dumps({"results": results}, ensure_ascii=False)