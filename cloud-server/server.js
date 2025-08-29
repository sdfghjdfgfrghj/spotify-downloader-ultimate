const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const { promisify } = require('util');

const app = express();
const port = process.env.PORT || 3000;
const execAsync = promisify(exec);

// Try to get FFmpeg path
let ffmpegPath = 'ffmpeg'; // Default fallback
try {
  const ffmpegInstaller = require('@ffmpeg-installer/ffmpeg');
  ffmpegPath = ffmpegInstaller.path;
  console.log('✅ FFmpeg installer found:', ffmpegPath);
} catch (e) {
  console.log('⚠️ Using system FFmpeg');
}

// Middleware
app.use(cors());
app.use(express.raw({ type: 'audio/*', limit: '50mb' }));

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({ 
    status: 'ok', 
    message: 'Audio converter API is running',
    timestamp: new Date().toISOString(),
    ffmpeg: ffmpegPath,
    endpoints: {
      convert: '/api/convert (POST)',
      health: '/api/health (GET)'
    }
  });
});

// Convert endpoint
app.post('/api/convert', async (req, res) => {
  try {
    console.log('🎵 Audio conversion request received');
    
    const buffer = req.body;
    
    if (!buffer || buffer.length === 0) {
      return res.status(400).json({ error: 'No audio data received' });
    }

    console.log(`📁 Received audio file: ${buffer.length} bytes`);

    // Create temporary file paths
    const tempDir = process.platform === 'win32' ? process.env.TEMP || 'C:\\temp' : '/tmp';
    const inputPath = path.join(tempDir, `input_${Date.now()}.m4a`);
    const outputPath = path.join(tempDir, `output_${Date.now()}.mp3`);

    // Write input file
    fs.writeFileSync(inputPath, buffer);
    console.log(`💾 Saved input file: ${inputPath}`);

    // Convert using FFmpeg command
    const ffmpegCommand = `"${ffmpegPath}" -y -i "${inputPath}" -vn -acodec libmp3lame -b:a 192k "${outputPath}"`;
    console.log('🎛️ FFmpeg command:', ffmpegCommand);
    
    try {
      const { stdout, stderr } = await execAsync(ffmpegCommand, { timeout: 30000 });
      console.log('✅ Conversion completed');
      if (stderr) console.log('FFmpeg stderr:', stderr);
    } catch (execError) {
      console.error('❌ FFmpeg execution error:', execError);
      throw new Error(`FFmpeg failed: ${execError.message}`);
    }

    // Read the converted file
    if (!fs.existsSync(outputPath)) {
      throw new Error('Output file was not created');
    }

    const outputBuffer = fs.readFileSync(outputPath);
    console.log(`📤 Sending MP3 file: ${outputBuffer.length} bytes`);

    // Clean up temporary files
    try {
      fs.unlinkSync(inputPath);
      fs.unlinkSync(outputPath);
      console.log('🗑️ Cleaned up temporary files');
    } catch (cleanupErr) {
      console.warn('⚠️ Cleanup warning:', cleanupErr.message);
    }

    // Send the MP3 file
    res.setHeader('Content-Type', 'audio/mpeg');
    res.setHeader('Content-Length', outputBuffer.length);
    res.setHeader('Content-Disposition', 'attachment; filename="converted.mp3"');
    res.send(outputBuffer);

  } catch (error) {
    console.error('❌ Conversion error:', error);
    res.status(500).json({ 
      error: 'Conversion failed', 
      details: error.message 
    });
  }
});

// Root endpoint
app.get('/', (req, res) => {
  res.json({
    message: 'Audio Converter API',
    version: '1.0.0',
    endpoints: {
      health: '/api/health',
      convert: '/api/convert'
    }
  });
});

// Start server
app.listen(port, () => {
  console.log('🚀 Audio Converter API started!');
  console.log(`📍 Server running at: http://localhost:${port}`);
  console.log(`🧪 Health check: http://localhost:${port}/api/health`);
  console.log(`🎛️ FFmpeg path: ${ffmpegPath}`);
  console.log('🛑 Press Ctrl+C to stop');
});