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

_ytm = YTMusic()

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
            "artist":    "",
            "artist_url":"",
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

    # Сбор метаданных артиста альбома
    artists_all = [ar.get('name', '') for ar in album.get('artists', [])]
    artist_url = ''
    if album.get('artists'):
        aid = album['artists'][0].get('id', '')
        if aid:
            artist_url = f"https://music.youtube.com/channel/{aid}"

    result = {
        'album': album_title,
        'artist': ", ".join(artists_all),
        'artist_url': artist_url,
        'year': str(album.get('year', ''))[:4],
        'image_url': (album.get('thumbnails') or [{}])[-1].get('url', ''),
        'tracks': tracks
    }

    return json.dumps(result, ensure_ascii=False, default=str)


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

        def fetch_albums(browse_id: str, params: str):
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
                # Определяем, является ли item синглом по title секции
                is_single = False
                albums.append({
                    "id": sbid,
                    "name": item.get("title", ""),
                    "image_url": (item.get("thumbnails") or [{}])[0].get("url", ""),
                    "url": f"https://music.youtube.com/browse/{sbid}",
                    "is_single": is_single,
                    "year": year
                })

        # 1) Пробуем стандартные поля albums/singles
        for key in ("albums", "singles", "albumReleases", "singleReleases"):
            sec = art.get(key, {})
            bid = sec.get("browseId")
            params = sec.get("params")
            if bid and params:
                fetch_albums(bid, params)

        # 2) Если всё ещё пусто, ищем continuation в секциях «Top releases»
        if not albums:
            for section in art.get("sections", []):
                cont = section.get("continuations")
                if not cont:
                    continue
                # continuation может быть в nextContinuationData или nextEndpoint
                token = (
                    cont[0].get("nextContinuationData", {}).get("continuation")
                    or cont[0].get("nextEndpoint", {}).get("params")
                )
                if token:
                    try:
                        items = _ytm.get_artist_albums_continuation(token)
                    except Exception:
                        continue
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
                            "is_single": False,
                            "year": year
                        })
                    break

        # Топ-5 треков
        top_tracks = []
        for t in art.get("songs", {}).get("results", [])[:5]:
            top_tracks.append({
                "id": t.get("videoId", ""),
                "name": t.get("title", ""),
                "album": t.get("album", {}).get("name", ""),
                "album_image_url": (t.get("thumbnails") or [{}])[0].get("url", ""),
                "preview_url": None,
                "spotify_url": f"https://music.youtube.com/watch?v={t.get('videoId','')}",
                "popularity": 0
            })

        result = {
            "type": "artist",
            "artist": art.get("name", ""),
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
