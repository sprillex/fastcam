# ProGuard/R8 rules for rtsp-viewer

# Keep LibVLC native bindings and components
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }

# Keep App components (Activities, Receivers)
-keep class com.sprillex.rtspviewer.** { *; }
