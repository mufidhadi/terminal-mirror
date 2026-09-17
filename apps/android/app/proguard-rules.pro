# AndroidX Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# MessagePack & Jackson
-keep class org.msgpack.** { *; }
-keep class com.fasterxml.jackson.** { *; }

# Terminal Mirror Models
-keep class com.mufid.terminalmirror.model.** { *; }
-keep class com.mufid.terminalmirror.terminal.** { *; }

# Suppress missing Java SE references in Jackson / Msgpack
-dontwarn java.beans.**
-dontwarn sun.nio.ch.**
