# ProGuard/R8 rules for rtsp-viewer

# Keep Media3 ExoPlayer core classes and components loaded reflectively
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.container.** { *; }
-keep class androidx.media3.ui.** { *; }

# Keep RTSP media source dynamic loading and protocol classes
-keep class androidx.media3.exoplayer.rtsp.** { *; }

# Keep App components (Activities, Receivers)
-keep class com.sprillex.rtspviewer.** { *; }
