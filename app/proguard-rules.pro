# Keep the JavaScript bridges used for downloads and the new-tab page.
-keepclassmembers class com.aegis.browser.web.BlobBridge {
    public *;
}
-keepclassmembers class com.aegis.browser.web.HomeBridge {
    public *;
}
