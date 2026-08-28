# QuestLog release (R8) rules.
#
# Room, RevenueCat, and Koin ship their own consumer rules in their artifacts, so
# nothing is needed here for them. Compose is handled by R8 directly. Add app-specific
# keeps below as the codebase grows.

# Keep enough to deobfuscate release crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx.serialization ────────────────────────────────────────────────────
# The kotlin-serialization plugin is applied. These are the rules from the
# official docs; harmless until a class is annotated @Serializable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer {
    *** serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
