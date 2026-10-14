// Zvuková zařízení macOS (CoreAudio), volané z Kotlinu přes JNI
// (eu.tintera.plume.core.calls.MacAudio). Seznam zařízení dává knihovna hovorů (ntgcalls);
// tady je jen to, co z něj nevyčte: typ připojení (podle něj ikona ve výběru výstupu)
// a výchozí výstup systému (ten je za hovoru vybraný, dokud si uživatel nezvolí jiný).

#import <CoreAudio/CoreAudio.h>
#import <Foundation/Foundation.h>
#include <jni.h>

static AudioObjectID plumeDeviceForUid(NSString *uid) {
    AudioObjectPropertyAddress address = {kAudioHardwarePropertyTranslateUIDToDevice, kAudioObjectPropertyScopeGlobal,
                                          kAudioObjectPropertyElementMain};
    CFStringRef value = (__bridge CFStringRef)uid;
    AudioObjectID device = kAudioObjectUnknown;
    UInt32 size = sizeof(device);
    if (AudioObjectGetPropertyData(kAudioObjectSystemObject, &address, sizeof(value), &value, &size, &device) != noErr) {
        return kAudioObjectUnknown;
    }
    return device;
}

// Typ připojení (kAudioDeviceTransportType…, čtyřznakový kód), 0 = neznámé zařízení.
JNIEXPORT jint JNICALL
Java_eu_tintera_plume_core_calls_MacAudio_nativeTransportType(JNIEnv *env, jclass cls, jstring uid) {
    const char *chars = (*env)->GetStringUTFChars(env, uid, NULL);
    NSString *value = [NSString stringWithUTF8String:chars];
    (*env)->ReleaseStringUTFChars(env, uid, chars);
    AudioObjectID device = plumeDeviceForUid(value);
    if (device == kAudioObjectUnknown) return 0;
    AudioObjectPropertyAddress address = {kAudioDevicePropertyTransportType, kAudioObjectPropertyScopeGlobal,
                                          kAudioObjectPropertyElementMain};
    UInt32 transport = 0;
    UInt32 size = sizeof(transport);
    if (AudioObjectGetPropertyData(device, &address, 0, NULL, &size, &transport) != noErr) return 0;
    return (jint)transport;
}

// UID výchozího výstupu systému, `null`, když ho nejde zjistit.
JNIEXPORT jstring JNICALL
Java_eu_tintera_plume_core_calls_MacAudio_nativeDefaultOutputUid(JNIEnv *env, jclass cls) {
    AudioObjectPropertyAddress address = {kAudioHardwarePropertyDefaultOutputDevice, kAudioObjectPropertyScopeGlobal,
                                          kAudioObjectPropertyElementMain};
    AudioObjectID device = kAudioObjectUnknown;
    UInt32 size = sizeof(device);
    if (AudioObjectGetPropertyData(kAudioObjectSystemObject, &address, 0, NULL, &size, &device) != noErr ||
        device == kAudioObjectUnknown) {
        return NULL;
    }
    AudioObjectPropertyAddress uidAddress = {kAudioDevicePropertyDeviceUID, kAudioObjectPropertyScopeGlobal,
                                             kAudioObjectPropertyElementMain};
    CFStringRef uid = NULL;
    size = sizeof(uid);
    if (AudioObjectGetPropertyData(device, &uidAddress, 0, NULL, &size, &uid) != noErr || uid == NULL) return NULL;
    jstring result = (*env)->NewStringUTF(env, [(__bridge NSString *)uid UTF8String]);
    CFRelease(uid);
    return result;
}

// Změny zvukových zařízení za hovoru: připojená / odpojená zařízení a změna výchozího
// výstupu (macOS na něj po připojení sluchátek obvykle sám přepne). Kotlin dostane jen
// „něco se změnilo" (MacAudio.onDevicesChanged) a seznam si přečte znovu.

static JavaVM *audioVm;
static jclass audioClass;
static jmethodID audioOnDevicesChanged;
static dispatch_queue_t audioQueue;
static AudioObjectPropertyListenerBlock audioListener;

static const AudioObjectPropertyAddress audioDevicesAddress = {
    kAudioHardwarePropertyDevices, kAudioObjectPropertyScopeGlobal, kAudioObjectPropertyElementMain};
static const AudioObjectPropertyAddress audioDefaultOutputAddress = {
    kAudioHardwarePropertyDefaultOutputDevice, kAudioObjectPropertyScopeGlobal, kAudioObjectPropertyElementMain};

JNIEXPORT jboolean JNICALL
Java_eu_tintera_plume_core_calls_MacAudio_nativeInit(JNIEnv *env, jclass cls) {
    (*env)->GetJavaVM(env, &audioVm);
    audioClass = (*env)->NewGlobalRef(env, cls);
    audioOnDevicesChanged = (*env)->GetStaticMethodID(env, cls, "onDevicesChanged", "()V");
    audioQueue = dispatch_queue_create("eu.tintera.plume.audio-devices", DISPATCH_QUEUE_SERIAL);
    return audioOnDevicesChanged != NULL ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_calls_MacAudio_nativeWatch(JNIEnv *env, jclass cls, jboolean on) {
    if (on && audioListener == nil) {
        audioListener = ^(UInt32 count, const AudioObjectPropertyAddress *addresses) {
            JNIEnv *callbackEnv = NULL;
            if ((*audioVm)->GetEnv(audioVm, (void **)&callbackEnv, JNI_VERSION_1_8) == JNI_EDETACHED) {
                (*audioVm)->AttachCurrentThreadAsDaemon(audioVm, (void **)&callbackEnv, NULL);
            }
            if (callbackEnv == NULL) return;
            (*callbackEnv)->CallStaticVoidMethod(callbackEnv, audioClass, audioOnDevicesChanged);
            if ((*callbackEnv)->ExceptionCheck(callbackEnv)) (*callbackEnv)->ExceptionClear(callbackEnv);
        };
        AudioObjectAddPropertyListenerBlock(kAudioObjectSystemObject, &audioDevicesAddress, audioQueue, audioListener);
        AudioObjectAddPropertyListenerBlock(kAudioObjectSystemObject, &audioDefaultOutputAddress, audioQueue, audioListener);
    } else if (!on && audioListener != nil) {
        AudioObjectRemovePropertyListenerBlock(kAudioObjectSystemObject, &audioDevicesAddress, audioQueue, audioListener);
        AudioObjectRemovePropertyListenerBlock(kAudioObjectSystemObject, &audioDefaultOutputAddress, audioQueue, audioListener);
        audioListener = nil;
    }
}
