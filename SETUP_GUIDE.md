# 🚀 Setup Guide - Spotify Downloader Ultimate

## 📋 Prerequisites

### Required Software
- **Python 3.8+** - [Download here](https://python.org/downloads)
- **Android Studio** (for building APK) - [Download here](https://developer.android.com/studio)
- **Git** - [Download here](https://git-scm.com/downloads)

### Spotify Developer Account
- **Spotify Account** - Free or Premium
- **Developer App** - We'll create this together

## 🎯 Step 1: Spotify API Setup

### 1.1 Create Spotify App
1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Log in with your Spotify account
3. Click **"Create App"**
4. Fill in the form:
   ```
   App name: My Music Downloader
   App description: Personal music downloader for backup
   Website: http://localhost:8080
   Redirect URI: http://localhost:8080
   ```
5. Check **"I understand and agree"**
6. Click **"Save"**

### 1.2 Get Your Credentials
1. Click on your newly created app
2. Click **"Settings"**
3. Copy your **Client ID**
4. Click **"View client secret"** and copy **Client Secret**
5. **IMPORTANT**: Keep these secret! Never share them publicly.

## 🐍 Step 2: Python Desktop Setup

### 2.1 Clone Repository
```bash
git clone https://github.com/sdfghjdfgfrghj/spotify-downloader-ultimate.git
cd spotify-downloader-ultimate
```

### 2.2 Install Dependencies
```bash
# Create virtual environment (recommended)
python -m venv venv

# Activate virtual environment
# On Windows:
venv\Scripts\activate
# On macOS/Linux:
source venv/bin/activate

# Install requirements
pip install -r requirements.txt
```

### 2.3 Configure Spotify API
1. Copy `spotify_config_template.json` to `spotify_config.json`
2. Edit `spotify_config.json` with your credentials:
   ```json
   {
     "client_id": "your_client_id_here",
     "client_secret": "your_client_secret_here", 
     "redirect_uri": "http://localhost:8080"
   }
   ```

### 2.4 First Run
```bash
python spotify_downloader_ultimate.py
```

**What happens:**
1. Browser opens for Spotify authentication
2. Log in and authorize the app
3. Browser redirects to localhost (this is normal!)
4. Copy the full URL from browser and paste in terminal
5. Downloads start automatically!

## 📱 Step 3: Android App Setup

### 3.1 Quick Option: Download APK
1. Go to [Releases](https://github.com/sdfghjdfgfrghj/spotify-downloader-ultimate/releases)
2. Download latest APK
3. Install on Android device
4. Enter your Spotify credentials in app settings

### 3.2 Build from Source
```bash
# Open in Android Studio
cd android-app
./gradlew assembleDebug

# APK will be in: app/build/outputs/apk/debug/
```

### 3.3 Configure Android App
1. Open app and go to **Settings**
2. Enter your **Spotify Client ID** and **Client Secret**
3. Set **Server URL**:
   - Local: `http://192.168.1.XXX:3000` (your computer's IP)
   - Cloud: Your deployed server URL
4. Test connection and start downloading!

## ☁️ Step 4: Cloud Server Setup (Optional)

### 4.1 Deploy to Vercel (Easiest)
1. Fork this repository
2. Go to [Vercel](https://vercel.com)
3. Import your forked repository
4. Deploy automatically!
5. Use the Vercel URL in your Android app

### 4.2 Deploy to Railway
1. Go to [Railway](https://railway.app)
2. Connect GitHub and select this repository
3. Deploy the `cloud-server` folder
4. Use the Railway URL in your Android app

### 4.3 Local Server
```bash
cd cloud-server
npm install
npm start
# Server runs on http://localhost:3000
```

## 🔧 Troubleshooting

### Common Issues

#### "Module not found" Error
```bash
pip install -r requirements.txt
```

#### "Python not found"
- Install Python from python.org
- Make sure "Add to PATH" is checked during installation

#### "Spotify authentication failed"
- Check your Client ID and Client Secret
- Make sure redirect URI is exactly `http://localhost:8080`
- Try clearing browser cache

#### "No songs found"
- Make sure you have liked songs in Spotify
- Check your Spotify account has access to the songs
- Some songs may not be available on YouTube

#### Android App Won't Install
- Enable "Unknown Sources" in Android settings
- Make sure APK is downloaded completely
- Try downloading APK again

#### Server Connection Failed
- Check server is running (`npm start` or `python server.py`)
- Verify IP address is correct
- Make sure firewall allows connections
- Try using `http://` not `https://` for local servers

### Getting Help

1. **Check Issues**: [GitHub Issues](https://github.com/sdfghjdfgfrghj/spotify-downloader-ultimate/issues)
2. **Read Documentation**: [Wiki](https://github.com/sdfghjdfgfrghj/spotify-downloader-ultimate/wiki)
3. **Ask Questions**: [Discussions](https://github.com/sdfghjdfgfrghj/spotify-downloader-ultimate/discussions)

## 🎯 Tips for Best Results

### Audio Quality
- Use **Spotify Premium** for best results
- Set **320kbps** in settings for highest quality
- Ensure stable internet connection

### Large Libraries
- Download in batches (50-100 songs at a time)
- Use cloud server for better performance
- Be patient - quality takes time!

### Privacy & Security
- Never share your Spotify credentials
- Use local server when possible
- Keep your API keys secret
- Regularly update the application

## ✅ Success Checklist

Before you start downloading, make sure:

- [ ] Python 3.8+ installed
- [ ] Spotify Developer App created
- [ ] Client ID and Client Secret copied
- [ ] Dependencies installed (`pip install -r requirements.txt`)
- [ ] Config file created (`spotify_config.json`)
- [ ] First authentication completed
- [ ] Server running (if using Android app)
- [ ] Android app configured (if using mobile)

## 🎉 You're Ready!

Once everything is set up:
1. **Python**: Run `python spotify_downloader_ultimate.py`
2. **Android**: Open app and start downloading
3. **Enjoy**: Your music collection offline!

---

**Need help? Don't hesitate to ask in the Issues or Discussions!** 💙