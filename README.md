# Voice Detector (For Android Java)

Voice Detector is a handy module that detects audio input from android and returns it as a decibel value.

## How to use

### Step. 1
This module need RECORD_AUDIO. Add the following inside the <manifest> tag in Manifest.xml
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```
RECORD_AUDIO is considered a 'dangerous' permission because it may pose a risk to the user's privacy. Starting with Android 6.0 (API level 23), apps using dangerous permissions must ask the user for permission at runtime. See the Android developer documentation below to learn how to request runtime permissions (dangerous permissions).

https://developer.android.com/guide/topics/permissions/overview#runtime
	
### Step. 2
Add it in your root build.gradle (Project) at the end of repositories:
```gradle
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```
### Step. 3
Add the dependency in build.gradle(Module) and click "sync now".

```gradle
dependencies {
        implementation 'com.github.mangneung:VoiceDetector:0.0.4'
}
```

### Step. 4
in your android component
```java
VoiceDetector mDetector = new VoiceDetector(this);
mDetector.startReader(new VoiceDetector.Listener() {
    @Override
    public void onReadComplete(int decibel) {
        Log.e("Detected: ", decibel + "dB");
    }

    @Override
    public void onReadError(int error) {

    }
});
```
	
### Step. 5
Now you just have to define operations you want in the overrided methods.


## License

MIT License
