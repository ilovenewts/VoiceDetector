# Voice Detector (For Android Java)

Voice Detector is a handy module that detects audio input from android and returns it as a decibel value.

## How to use

Add it in your root build.gradle at the end of repositories:
```gradle
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```
Add the dependency and click "sync now".

```gradle
dependencies {
        implementation 'com.github.mangneung:VoiceDetector:0.0.4'
}
```


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

## License

MIT License
