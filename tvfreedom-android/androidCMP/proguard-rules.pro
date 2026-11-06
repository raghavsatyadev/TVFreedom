# Release R8 rules. Libraries ship their own consumer rules; these cover what they cannot see.

# libadb loads Conscrypt by reflection for TLS pairing; Conscrypt's native code calls back into it.
-keep class org.conscrypt.** { *; }
-keep class io.github.muntashirakon.adb.** { *; }
# bcpkix builds the ADB key certificate; its providers are found by name.
-keep class org.bouncycastle.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# ML Kit (QR pairing) finds its registrars by name and builds them through the no-argument
# constructor; full-mode R8 drops it ("NoSuchMethodException ... Registrar.<init>").
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
