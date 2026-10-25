#include <jni.h>
#include <jvmti.h>
#include <iostream>

// Consistent JNI/JVMTI bridge - no game logic here, just signal forwarding
// Compile as utilitymod-native.dll and place on java.library.path

static JavaVM* gJvm = nullptr;
static jclass gBridgeClass = nullptr;
static jmethodID gSignalMethod = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    gJvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_8) != JNI_OK) return JNI_VERSION_1_8;

    jclass cls = env->FindClass("com/utilitymod/jni/NativeBridge");
    if (cls) {
        gBridgeClass = (jclass)env->NewGlobalRef(cls);
        gSignalMethod = env->GetStaticMethodID(gBridgeClass, "nativeSignalJNI", "(IJ)V");
    }
    return JNI_VERSION_1_8;
}

// Called from JVMTI Agent or other native hook to toggle safely
extern "C" void NativeSignal(int code, long long payload) {
    if (!gJvm || !gBridgeClass || !gSignalMethod) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (gJvm->GetEnv((void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        gJvm->AttachCurrentThread((void**)&env, nullptr);
        attached = true;
    }
    env->CallStaticVoidMethod(gBridgeClass, gSignalMethod, (jint)code, (jlong)payload);
    if (attached) gJvm->DetachCurrentThread();
}

// Java -> Native
extern "C" JNIEXPORT void JNICALL Java_com_utilitymod_jni_NativeBridge_notifyModuleToggle(JNIEnv* env, jclass, jstring name, jboolean enabled) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    std::cout << "[utilitymod-native] toggle " << cname << " -> " << (enabled ? "on" : "off") << std::endl;
    env->ReleaseStringUTFChars(name, cname);
}

extern "C" JNIEXPORT void JNICALL Java_com_utilitymod_jni_NativeBridge_pollNativeEvents(JNIEnv*, jclass) {
    // poll JVMTI events if needed - keep empty for now
}

// JVMTI Agent entry (if loaded as -javaagent or via Attach API alongside fabric-injector)
// This shows injection handled by JVMTI consistently
extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
    gJvm = vm;
    return JNI_OK;
}
extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options, void* reserved) {
    gJvm = vm;
    return JNI_OK;
}
