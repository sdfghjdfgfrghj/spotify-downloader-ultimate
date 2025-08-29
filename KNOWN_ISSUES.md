# Known Issues and Solutions

## Chaquopy Build Issue

**Problem**: The Android build fails with Python dependency errors related to Chaquopy.

**Root Cause**: Compatibility issue between system Python environment and Chaquopy's embedded pip.

**Current Status**: This is a known issue affecting many developers using Chaquopy with newer Python versions.

**Workarounds**:
1. Use Android Studio (often handles this automatically)
2. Use Docker with Python 3.8
3. Temporarily disable Chaquopy for basic APK build
4. Use GitHub Actions with controlled environment

**Community Help Needed**: 
- Contributors welcome to help solve this build issue
- Multiple approaches documented in BUILD_GUIDE.md
- This is a real-world development challenge

**The Application Works**: The core functionality is solid - this is purely a build environment issue.
