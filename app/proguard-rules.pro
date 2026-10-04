# Project-specific R8 rules. Most AndroidX, Hilt and kotlinx.serialization rules ship with the libraries.
# https://developer.android.com/build/shrink-code

# web3j binds JSON-RPC responses with Jackson and builds ABI types from TypeReference generics,
# both by reflection, and ships no consumer rules.
-keep class org.web3j.protocol.core.** { *; }
-keep class org.web3j.protocol.http.** { *; }
-keep class org.web3j.abi.** { *; }
-keep class org.web3j.ens.** { *; }
# R8 drops a subclass's generic signature unless TypeReference itself keeps its own (Missing type parameter).
-keep class * extends org.web3j.abi.TypeReference
-keep class * extends org.web3j.abi.datatypes.StaticStruct { *; }
-keep class * extends org.web3j.abi.datatypes.DynamicStruct { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Jackson (via web3j) looks up these at runtime and tolerates their absence.
-keep class com.fasterxml.jackson.databind.ObjectMapper { *; }
-dontwarn com.fasterxml.jackson.databind.ext.**
-dontwarn java.beans.**

# Optional dependencies of web3j that this app excludes (desktop transports, AWS KMS, KZG blobs).
-dontwarn org.web3j.**
-dontwarn jnr.**
-dontwarn org.java_websocket.**
-dontwarn software.amazon.awssdk.**
-dontwarn ethereum.ckzg4844.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
