# R8 rules for the shrunk release APK.
#
# Room, Media3 and WorkManager ship their own consumer rules, so nothing is
# needed for them here. What follows covers the two things R8 cannot see:
# classes reached only through kotlinx.serialization's generated serializers,
# and the entry points Android instantiates by name.
#
# A missing rule here does not fail the build — it fails on the tablet, at the
# moment a seed file is parsed or a backup is restored. Treat any new
# reflective access as a change that needs a rule.

# ---------------------------------------------------------------- kotlinx.serialization
# The compiler plugin writes a `Companion.serializer()` for every @Serializable
# class; R8 sees no caller for it and would strip both. Used by the seed loader,
# the doha asset catalogue and backup/restore.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

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

-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------- app entry points
# Declared in the manifest and instantiated by the framework by name. AGP keeps
# manifest components already; these are spelled out because losing the service
# is the one failure that looks exactly like "the appliance stopped ringing".
-keep class org.dhamma.gong.service.** { *; }

# The domain layer is the scheduling contract and is covered by unit tests that
# do not run against the shrunk APK. Keeping it costs a few KB and removes a
# whole class of "works in debug, silent in release".
-keep class org.dhamma.gong.domain.** { *; }

# ---------------------------------------------------------------- diagnostics
# Line numbers survive so a tester's crash report still points at a source line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
