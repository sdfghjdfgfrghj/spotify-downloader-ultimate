#!/usr/bin/env python3
"""
Ultimate Spotify Downloader - FIXED VERSION
Addresses YouTube download issues, fingerprint problems, and crash bugs
"""

import os
import sys
import json
import time
import requests
import spotipy
from spotipy.oauth2 import SpotifyOAuth
import yt_dlp
from pathlib import Path
import subprocess
from urllib.request import urlretrieve
import tempfile

# Import cloud audio converter for conversion
try:
    from cloud_audio_converter import convert_audio_to_mp3_cloud, is_cloud_audio_available
    CLOUD_CONVERTER_AVAILABLE = True
    print("☁️ Cloud audio converter loaded")
except ImportError:
    print("⚠️ Cloud audio converter not available - M4A files will not be converted to MP3")
    CLOUD_CONVERTER_AVAILABLE = False
    
    def convert_audio_to_mp3_cloud(*args, **kwargs):
        return False
    
    def is_cloud_audio_available():
        return False

class UltimateSpotifyDownloader:
    def __init__(self):
        self.spotify = None
        self.download_dir = "downloaded_music"
        self.temp_dir = "temp_downloads"
        self.config_file = "spotify_config.json"
        self.failed_songs = []
        self.custom_output_uri = None  # For user-selected folder
        self.setup_directories()
        
    def setup_directories(self):
        """Create necessary directories"""
        Path(self.download_dir).mkdir(exist_ok=True)
        Path(self.temp_dir).mkdir(exist_ok=True)
        
    def setup_spotify_auth(self):
        """Setup Spotify API authentication"""
        print("🎵 Setting up Spotify authentication...")
        
        if os.path.exists(self.config_file):
            with open(self.config_file, 'r') as f:
                config = json.load(f)
        else:
            print("\n📝 First time setup - you need Spotify API credentials")
            print("1. Go to https://developer.spotify.com/dashboard")
            print("2. Create a new app")
            print("3. Add 'http://localhost:8080' as redirect URI")
            print("4. Copy your Client ID and Client Secret\n")
            
            client_id = input("Enter your Spotify Client ID: ").strip()
            client_secret = input("Enter your Spotify Client Secret: ").strip()
            
            config = {
                'client_id': client_id,
                'client_secret': client_secret,
                'redirect_uri': 'http://localhost:8080'
            }
            
            with open(self.config_file, 'w') as f:
                json.dump(config, f, indent=2)
        
        try:
            auth_manager = SpotifyOAuth(
                client_id=config['client_id'],
                client_secret=config['client_secret'],
                redirect_uri=config['redirect_uri'],
                scope="user-library-read",
                cache_path=".spotify_cache"
            )
            
            self.spotify = spotipy.Spotify(auth_manager=auth_manager)
            user = self.spotify.current_user()
            print(f"✅ Connected to Spotify as: {user['display_name']}")
            return True
            
        except Exception as e:
            print(f"❌ Spotify authentication failed: {e}")
            return False
    
    def get_liked_songs(self):
        """Get all liked songs from Spotify"""
        print("📥 Fetching your liked songs...")
        
        liked_songs = []
        offset = 0
        limit = 50
        
        while True:
            try:
                results = self.spotify.current_user_saved_tracks(limit=limit, offset=offset)
                
                if not results['items']:
                    break
                    
                for item in results['items']:
                    track = item['track']
                    # Get the highest quality album artwork
                    album_art_url = None
                    if track['album']['images']:
                        # Spotify provides images in descending order of size
                        album_art_url = track['album']['images'][0]['url']
                    
                    song_info = {
                        'name': track['name'],
                        'artist': ', '.join([artist['name'] for artist in track['artists']]),
                        'album': track['album']['name'],
                        'duration': track['duration_ms'],
                        'spotify_id': track['id'],
                        'album_art_url': album_art_url
                    }
                    liked_songs.append(song_info)
                
                offset += limit
                print(f"📥 Fetched {len(liked_songs)} songs so far...")
                
            except Exception as e:
                print(f"❌ Error fetching songs: {e}")
                break
            
        print(f"✅ Found {len(liked_songs)} liked songs!")
        return liked_songs
        
    def search_youtube(self, song_info):
        """Search for song on YouTube with better error handling"""
        query = f"{song_info['artist']} - {song_info['name']}"
        
        # Simplified ydl options to avoid authentication issues and ffmpeg
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
            'default_search': 'ytsearch3:',  # Reduced to 3 results for faster processing
            'prefer_ffmpeg': False,  # Disable ffmpeg for search too
            'postprocessors': [],    # No postprocessors
            'extractor_args': {
                'youtube': {
                    'skip': ['dash', 'hls']  # Skip problematic formats
                }
            }
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            try:
                search_results = ydl.extract_info(query, download=False)
                return search_results['entries'] if search_results else []
            except Exception as e:
                print(f"❌ YouTube search failed for {query}: {e}")
                return []
                
    def download_audio_robust(self, video_url, temp_filename):
        """Download audio with robust error handling and NO ffmpeg usage"""
        
        # Completely disable ffmpeg to avoid permission issues
        ydl_opts = {
            'format': 'bestaudio[ext=m4a]/bestaudio/best',  # Prefer m4a for better compatibility
            'outtmpl': f"{temp_filename}.%(ext)s",
            'quiet': True,
            'no_warnings': True,
            'prefer_ffmpeg': False,  # Explicitly disable ffmpeg
            'postprocessors': [],    # NO post-processing to avoid ffmpeg
            'fixup': 'never',        # Don't try to fix files with ffmpeg
            'extractor_args': {
                'youtube': {
                    'skip': ['dash', 'hls'],  # Skip problematic formats
                    'player_skip': ['configs']  # Skip player config that might require auth
                }
            },
            'writethumbnail': False,  # Disable thumbnail to avoid ffmpeg
            'writeinfojson': False,
            'embed_subs': False,
            'writesubtitles': False
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            try:
                print(f"📥 Downloading from: {video_url}")
                ydl.download([video_url])
                print(f"✅ Download completed")
                return True
            except Exception as e:
                print(f"❌ Download failed: {e}")
                # Try alternative format if first attempt fails
                try:
                    print("🔄 Trying alternative format...")
                    ydl_opts['format'] = 'worst[ext=mp4]/worst'  # Try worst quality as fallback
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl2:
                        ydl2.download([video_url])
                        print(f"✅ Alternative download completed")
                        return True
                except Exception as e2:
                    print(f"❌ Alternative download also failed: {e2}")
                    return False

    def get_safe_filename(self, song_info):
        """Get safe filename for the song"""
        filename = f"{song_info['artist']} - {song_info['name']}"
        # Remove invalid characters
        invalid_chars = '<>:"/\\|?*'
        for char in invalid_chars:
            filename = filename.replace(char, '')
        # Replace multiple spaces with single space
        filename = ' '.join(filename.split())
        return filename[:200]  # Limit length

    def download_album_art(self, album_art_url, temp_dir):
        """Download album artwork from Spotify"""
        if not album_art_url:
            return None
            
        try:
            # Create a temporary file for the album art
            temp_art_file = os.path.join(temp_dir, "temp_album_art.jpg")
            
            # Download the album art
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            }
            response = requests.get(album_art_url, headers=headers, timeout=10)
            response.raise_for_status()
            
            with open(temp_art_file, 'wb') as f:
                f.write(response.content)
                
            print(f"🖼️  Downloaded album artwork")
            return temp_art_file
            
        except Exception as e:
            print(f"⚠️  Failed to download album art: {e}")
            return None

    def add_metadata_with_artwork(self, audio_file, song_info, temp_dir):
        """Add metadata including album artwork"""
        try:
            # Try using eyed3 if available
            import eyed3
            audiofile = eyed3.load(audio_file)
            if audiofile and audiofile.tag is None:
                audiofile.initTag()
                
            if audiofile and audiofile.tag:
                # Add basic metadata
                audiofile.tag.title = song_info['name']
                audiofile.tag.artist = song_info['artist']
                audiofile.tag.album = song_info['album']
                
                # Add album artwork
                album_art_file = None
                if song_info.get('album_art_url'):
                    album_art_file = self.download_album_art(song_info['album_art_url'], temp_dir)
                
                if album_art_file and os.path.exists(album_art_file):
                    try:
                        with open(album_art_file, 'rb') as art_file:
                            audiofile.tag.images.set(
                                eyed3.id3.frames.ImageFrame.FRONT_COVER,
                                art_file.read(),
                                'image/jpeg'
                            )
                        print(f"🖼️  Embedded album artwork")
                        # Clean up temp art file
                        os.remove(album_art_file)
                    except Exception as e:
                        print(f"⚠️  Failed to embed artwork: {e}")
                
                audiofile.tag.save()
                print(f"✅ Metadata and artwork added to {song_info['name']}")
            
        except ImportError:
            print("⚠️  eyed3 not available, skipping metadata")
        except Exception as e:
            print(f"⚠️  Failed to add metadata: {e}")

    def add_basic_metadata(self, audio_file, song_info):
        """Add basic metadata without complex dependencies - kept for compatibility"""
        self.add_metadata_with_artwork(audio_file, song_info, self.temp_dir)
    
    def copy_to_custom_folder(self, source_file, song_info):
        """Copy downloaded file to user-selected custom folder"""
        try:
            from java import jclass
            UiBridge = jclass("org.example.spotifydownloader.UiBridge")
            
            filename = os.path.basename(source_file)
            success = UiBridge.copyToCustomFolder(source_file, filename, self.custom_output_uri)
            
            if success:
                print(f"📁 Copied to custom folder: {filename}")
            else:
                print(f"⚠️ Failed to copy to custom folder: {filename}")
                
        except Exception as e:
            print(f"⚠️ Custom folder copy error: {e}")

    def download_song(self, song_info):
        """Download a single song with robust error handling"""
        print(f"\n🎵 Downloading: {song_info['artist']} - {song_info['name']}")
        
        # Check if already downloaded
        safe_filename = self.get_safe_filename(song_info)
        final_path = os.path.join(self.download_dir, f"{safe_filename}.mp3")
        
        if os.path.exists(final_path):
            print(f"⏭️  Already downloaded: {song_info['name']}")
            return True
            
        # Search YouTube
        youtube_results = self.search_youtube(song_info)
        if not youtube_results:
            print(f"❌ No YouTube results found for {song_info['name']}")
            self.failed_songs.append(song_info)
            return False
            
        # Try each YouTube result
        for i, video in enumerate(youtube_results):
            print(f"🔄 Trying result {i+1}/{len(youtube_results)}: {video.get('title', 'Unknown')}")
            
            temp_file = os.path.join(self.temp_dir, f"temp_{safe_filename}")
            
            # Download audio
            if not self.download_audio_robust(video['webpage_url'], temp_file):
                continue
            
            # Wait for download to complete
            print("⏳ Waiting for download to complete...")
            time.sleep(3)
                
            # Find the downloaded file
            possible_files = [
                f"{temp_file}.mp3",
                f"{temp_file}.m4a", 
                f"{temp_file}.webm",
                f"{temp_file}.opus",
                f"{temp_file}.mp4"
            ]
            
            temp_audio = None
            for possible_file in possible_files:
                if os.path.exists(possible_file) and os.path.getsize(possible_file) > 10000:  # At least 10KB
                    temp_audio = possible_file
                    print(f"📁 Found downloaded file: {temp_audio} ({os.path.getsize(possible_file)} bytes)")
                    break
            
            # Clean up any downloaded thumbnail files
            possible_thumbnails = [
                f"{temp_file}.jpg",
                f"{temp_file}.jpeg", 
                f"{temp_file}.png",
                f"{temp_file}.webp"
            ]
            for thumb_file in possible_thumbnails:
                if os.path.exists(thumb_file):
                    try:
                        os.remove(thumb_file)
                    except:
                        pass
            
            if not temp_audio:
                print(f"❌ No downloaded file found")
                continue
                
            # Move to final location
            try:
                # If the downloaded file is not MP3, convert it using cloud server
                if not temp_audio.endswith('.mp3'):
                    print(f"🌐 Converting {temp_audio} to MP3 via cloud server...")
                    mp3_temp = f"{temp_file}_converted.mp3"
                    
                    # Try cloud conversion first
                    conversion_success = False
                    if CLOUD_CONVERTER_AVAILABLE and is_cloud_audio_available() and convert_audio_to_mp3_cloud(temp_audio, mp3_temp):
                        print("✅ Successfully converted to MP3 via cloud")
                        conversion_success = True
                    else:
                        print("⚠️ Cloud conversion failed, trying local FFmpeg...")
                        # Try local FFmpeg as fallback
                        try:
                            from java import jclass
                            UiBridge = jclass("org.example.spotifydownloader.UiBridge")
                            
                            # Use Android FFmpeg via UiBridge
                            success = UiBridge.convertAudioToMp3(temp_audio, mp3_temp)
                            if success:
                                print("✅ Successfully converted to MP3 via local FFmpeg")
                                conversion_success = True
                            else:
                                print("❌ Local FFmpeg conversion also failed")
                        except Exception as e:
                            print(f"❌ Local FFmpeg fallback error: {e}")
                    
                    if conversion_success:
                        # Remove original file and use converted MP3
                        try:
                            os.remove(temp_audio)
                        except:
                            pass
                        temp_audio = mp3_temp
                    else:
                        print("⚠️ All conversion methods failed, keeping original format")
                        # Update final path to match original format
                        final_path = final_path.replace('.mp3', os.path.splitext(temp_audio)[1])
                
                os.rename(temp_audio, final_path)
                print(f"📁 Moved to: {final_path}")
                
                # Add basic metadata
                self.add_basic_metadata(final_path, song_info)
                
                # Copy to custom folder if specified
                if self.custom_output_uri:
                    self.copy_to_custom_folder(final_path, song_info)
                
                print(f"✅ Successfully downloaded: {song_info['name']}")
                return True
                
            except Exception as e:
                print(f"❌ Failed to move file: {e}")
                continue
                
        print(f"❌ Could not download: {song_info['name']}")
        self.failed_songs.append(song_info)
        return False

    def save_failed_songs(self):
        """Save list of failed songs for retry"""
        if self.failed_songs:
            failed_file = "failed_downloads.json"
            with open(failed_file, 'w') as f:
                json.dump(self.failed_songs, f, indent=2)
            print(f"💾 Failed songs saved to: {failed_file}")

    def download_all(self):
        """Download all liked songs"""
        print("🚀 Starting Ultimate Spotify Downloader\n")
        
        # Setup
        if not self.setup_spotify_auth():
            return
            
        liked_songs = self.get_liked_songs()
        
        if not liked_songs:
            print("❌ No liked songs found!")
            return
            
        print(f"\n📥 Starting download of {len(liked_songs)} songs...")
        print("🔧 Using robust mode - no fingerprinting, no video clips")
        print(f"🌐 Cloud converter available for M4A→MP3 conversion: {is_cloud_audio_available()}")
        
        successful = 0
        failed = 0
        
        for i, song in enumerate(liked_songs, 1):
            print(f"\n[{i}/{len(liked_songs)}]", end=" ")
            
            try:
                if self.download_song(song):
                    successful += 1
                else:
                    failed += 1
            except KeyboardInterrupt:
                print("\n⏹️  Download interrupted by user")
                break
            except Exception as e:
                print(f"❌ Unexpected error: {e}")
                failed += 1
                self.failed_songs.append(song)
                
            # Small delay to be nice to APIs
            time.sleep(2)
            
        print(f"\n🎉 Download complete!")
        print(f"✅ Successful: {successful}")
        print(f"❌ Failed: {failed}")
        print(f"📁 Files saved to: {os.path.abspath(self.download_dir)}")
        
        # Save failed songs for retry
        self.save_failed_songs()
        
        if failed > 0:
            print(f"\n🔄 To retry failed downloads, run the script again")
            print(f"   Failed songs are saved in failed_downloads.json")

def main():
    print("🎵 Ultimate Spotify Downloader - Fixed Version")
    print("=" * 50)
    print("🔧 This version fixes YouTube auth issues and crashes")
    print("📱 Audio-only mode for maximum stability\n")
    
    downloader = UltimateSpotifyDownloader()
    downloader.download_all()

if __name__ == "__main__":
    main()