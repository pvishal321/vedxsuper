# VedxSuper ProGuard Rules
-keepattributes Signature, *Annotation*, EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Market Models
-keep class com.vedx.vedxsuper.model.market.** { *; }

# Keep Strategy Results
-keep class com.vedx.vedxsuper.strategy.indicator.SuperTrendResult { *; }
-keep class com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult { *; }
-keep class com.vedx.vedxsuper.strategy.engine.CentralDecision { *; }
-keep class com.vedx.vedxsuper.strategy.engine.OptionIntelligenceScore { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile