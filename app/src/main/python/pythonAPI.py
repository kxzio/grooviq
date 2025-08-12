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

def searchOnServer(q: str) -> str:
    q = q.strip()
    if not q:
        return json.dumps({
            "tracks":  [],
            "albums":  [],
            "artists": [],
            "error":   "Empty query"
        })

    norm_q = q.lower()

    # 1) Делаем три запроса
    try:
        search_tracks  = _ytm.search(q, filter="songs",  limit=15)
        search_albums  = _ytm.search(q, filter="albums", limit=15)
        search_artists = _ytm.search(q, filter="artists",limit=15)
    except Exception as e:
        return json.dumps({
            "tracks":  [],
            "albums":  [],
            "artists": [],
            "error":   f"YTMusic API error: {e}"
        })


    def track_data(t):
        artists_list = t.get("artists") or []
        if artists_list:
            artist_str = ", ".join(a.get("name","") for a in artists_list if a.get("name"))
        else:
            artist_str = t.get("artist","") or t.get("channelName","")

        album_url = None
        if "album" in t and isinstance(t["album"], dict):
            album_id = t["album"].get("id")
            if album_id:
                album_url = f"https://music.youtube.com/browse/{album_id}"

        return {
            "type":      "track",
            "id":        t.get("videoId",""),
            "name":      t.get("title",""),
            "artist":    artist_str,
            "image_url": (t.get("thumbnails") or [{"url":""}])[-1].get("url",""),
            "album_url": album_url
        }

    def album_data(a):
        artist_str = a.get("artist") \
            or ", ".join(ar.get("name","") for ar in a.get("artists",[]) if ar.get("name")) \
            or a.get("subtitle","")
        return {
            "type":      "album",
            "id":        a.get("browseId",""),
            "name":      a.get("title",""),
            "artist":    artist_str,
            "image_url": (a.get("thumbnails") or [{"url":""}])[-1].get("url","")
        }

    def artist_data(ar):
        # Пробуем все варианты, где может лежать имя артиста
        name = (
            ar.get("name")
            or ar.get("title")
            or ar.get("artist")
            or ar.get("author")
            or ar.get("channelName")
            or ar.get("subtitle")
            or ""
        )
        # Если вдруг внутри есть список artists
        if not name and isinstance(ar.get("artists"), list):
            name = ", ".join(a.get("name","") for a in ar["artists"] if a.get("name"))
        return {
            "type":      "artist",
            "id":        ar.get("browseId",""),
            "name":      name,
            "image_url": (ar.get("thumbnails") or [{"url":""}])[-1].get("url","")
        }

    def ordered(raw_list, data_fn, check_fn):
        exact, fuzzy = [], []
        for item in raw_list:
            data = data_fn(item)
            if check_fn(item):
                exact.append(data)
            else:
                fuzzy.append(data)
        return exact + fuzzy

    ordered_tracks = ordered(
        search_tracks,
        track_data,
        lambda t: norm_q in t.get("title","").lower()
                  or any(norm_q in a.get("name","").lower() for a in t.get("artists",[]))
                  or norm_q in (t.get("artist") or "").lower()
    )
    ordered_albums = ordered(
        search_albums,
        album_data,
        lambda a: norm_q in a.get("title","").lower()
                  or norm_q in (a.get("artist") or "").lower()
    )
    ordered_artists = ordered(
        search_artists,
        artist_data,
        lambda ar: norm_q in (ar.get("name") or ar.get("title","")).lower()
    )

    result = {
        "tracks":  ordered_tracks,
        "albums":  ordered_albums,
        "artists": ordered_artists
    }
    return json.dumps(result)

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