// Náhled kamery a oprávnění kamery / mikrofonu na macOS přes AVFoundation, volané z Kotlinu
// přes JNI (eu.tintera.plume.core.calls.MacCamera). Překládá ho compileMacNotifications
// v client/shared/build.gradle.kts spolu s ostatním nativním kódem pro Mac.
//
// Náhled běží jen do spojení hovoru: pak kameru otevře knihovna hovorů (ntgcalls) sama a náhled
// se zastaví. Zařízení se vybírá stejně jako v knihovně (MacCameraCapturerModule): první
// z vestavěných a externích kamer, ať náhled i hovor ukazují tutéž.

#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CoreMedia.h>
#import <CoreVideo/CoreVideo.h>
#include <jni.h>

static JavaVM *cameraVm;
static jclass cameraClass;
static jmethodID cameraOnFrame;

@interface PlumeCameraPreview : NSObject <AVCaptureVideoDataOutputSampleBufferDelegate>
@property(nonatomic, strong) AVCaptureSession *session;
@property(nonatomic, strong) dispatch_queue_t queue;
@end

@implementation PlumeCameraPreview

// Snímek BGRA: Kotlin ho zkopíruje během volání (buffer je zamčený jen do návratu).
- (void)captureOutput:(AVCaptureOutput *)output
    didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
           fromConnection:(AVCaptureConnection *)connection {
    CVImageBufferRef pixels = CMSampleBufferGetImageBuffer(sampleBuffer);
    if (pixels == NULL || cameraVm == NULL) return;
    JNIEnv *env = NULL;
    if ((*cameraVm)->GetEnv(cameraVm, (void **)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        // Vlákna fronty GCD se opakují; démonické připojení nebrání ukončení JVM.
        (*cameraVm)->AttachCurrentThreadAsDaemon(cameraVm, (void **)&env, NULL);
    }
    if (env == NULL) return;
    CVPixelBufferLockBaseAddress(pixels, kCVPixelBufferLock_ReadOnly);
    void *base = CVPixelBufferGetBaseAddress(pixels);
    size_t stride = CVPixelBufferGetBytesPerRow(pixels);
    size_t width = CVPixelBufferGetWidth(pixels);
    size_t height = CVPixelBufferGetHeight(pixels);
    if (base != NULL) {
        jobject buffer = (*env)->NewDirectByteBuffer(env, base, (jlong)(stride * height));
        (*env)->CallStaticVoidMethod(env, cameraClass, cameraOnFrame, buffer, (jint)width, (jint)height, (jint)stride);
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, buffer);
    }
    CVPixelBufferUnlockBaseAddress(pixels, kCVPixelBufferLock_ReadOnly);
}

@end

static PlumeCameraPreview *cameraPreview;

static AVCaptureDevice *plumeCameraDevice(void) {
    AVCaptureDeviceDiscoverySession *discovery = [AVCaptureDeviceDiscoverySession
        discoverySessionWithDeviceTypes:@[ AVCaptureDeviceTypeBuiltInWideAngleCamera, AVCaptureDeviceTypeExternal ]
                              mediaType:AVMediaTypeVideo
                               position:AVCaptureDevicePositionUnspecified];
    return discovery.devices.firstObject;
}

// Jen v zabaleném Plume.app s popisy použití v Info.plist: bez nich macOS aplikaci při prvním
// sáhnutí na kameru nebo mikrofon UKONČÍ (holý `java` z ladicího běhu je nemá).
JNIEXPORT jboolean JNICALL
Java_eu_tintera_plume_core_calls_MacCamera_nativeInit(JNIEnv *env, jclass cls) {
    NSBundle *bundle = [NSBundle mainBundle];
    if (![[[bundle bundleURL] pathExtension] isEqualToString:@"app"] ||
        [bundle objectForInfoDictionaryKey:@"NSCameraUsageDescription"] == nil ||
        [bundle objectForInfoDictionaryKey:@"NSMicrophoneUsageDescription"] == nil) {
        return JNI_FALSE;
    }
    (*env)->GetJavaVM(env, &cameraVm);
    cameraClass = (*env)->NewGlobalRef(env, cls);
    cameraOnFrame = (*env)->GetStaticMethodID(env, cls, "onFrame", "(Ljava/nio/ByteBuffer;III)V");
    return cameraOnFrame != NULL ? JNI_TRUE : JNI_FALSE;
}

// Je oprávnění kamery (video = true) nebo mikrofonu už udělené? Bez dotazu.
JNIEXPORT jboolean JNICALL
Java_eu_tintera_plume_core_calls_MacCamera_nativeHasAccess(JNIEnv *env, jclass cls, jboolean video) {
    AVMediaType type = video ? AVMediaTypeVideo : AVMediaTypeAudio;
    return [AVCaptureDevice authorizationStatusForMediaType:type] == AVAuthorizationStatusAuthorized ? JNI_TRUE : JNI_FALSE;
}

// Oprávnění kamery (video = true) nebo mikrofonu. Nerozhodnuté se zeptá systémovým dotazem
// a čeká na odpověď — volá se mimo hlavní vlákno a mimo AWT.
JNIEXPORT jboolean JNICALL
Java_eu_tintera_plume_core_calls_MacCamera_nativeRequestAccess(JNIEnv *env, jclass cls, jboolean video) {
    AVMediaType type = video ? AVMediaTypeVideo : AVMediaTypeAudio;
    switch ([AVCaptureDevice authorizationStatusForMediaType:type]) {
        case AVAuthorizationStatusAuthorized:
            return JNI_TRUE;
        case AVAuthorizationStatusDenied:
        case AVAuthorizationStatusRestricted:
            return JNI_FALSE;
        default:
            break;
    }
    __block BOOL granted = NO;
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    [AVCaptureDevice requestAccessForMediaType:type
                             completionHandler:^(BOOL result) {
                                 granted = result;
                                 dispatch_semaphore_signal(done);
                             }];
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER);
    return granted ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_eu_tintera_plume_core_calls_MacCamera_nativeStart(JNIEnv *env, jclass cls) {
    if (cameraPreview != nil) return JNI_TRUE;
    AVCaptureDevice *device = plumeCameraDevice();
    if (device == nil) return JNI_FALSE;
    NSError *error = nil;
    AVCaptureDeviceInput *input = [AVCaptureDeviceInput deviceInputWithDevice:device error:&error];
    if (input == nil) return JNI_FALSE;

    PlumeCameraPreview *preview = [PlumeCameraPreview new];
    preview.queue = dispatch_queue_create("eu.tintera.plume.camera-preview", DISPATCH_QUEUE_SERIAL);
    AVCaptureSession *session = [AVCaptureSession new];
    if (![session canAddInput:input]) return JNI_FALSE;
    [session addInput:input];

    AVCaptureVideoDataOutput *output = [AVCaptureVideoDataOutput new];
    // Stejný rozměr, jaký pak chce knihovna (1280 × 720): náhled po spojení neposkočí a kopie
    // snímku je menší než u plného 1080p. Předvolba session (`AVCaptureSessionPreset1280x720`)
    // u vestavěné kamery MacBooku nezabrala a snímky chodily 1920 × 1080; zmenšení ve výstupu
    // udělá AVFoundation sama.
    output.videoSettings = @{
        (id)kCVPixelBufferPixelFormatTypeKey: @(kCVPixelFormatType_32BGRA),
        (id)kCVPixelBufferWidthKey: @1280,
        (id)kCVPixelBufferHeightKey: @720,
    };
    output.alwaysDiscardsLateVideoFrames = YES;
    [output setSampleBufferDelegate:preview queue:preview.queue];
    if (![session canAddOutput:output]) return JNI_FALSE;
    [session addOutput:output];

    preview.session = session;
    cameraPreview = preview;
    // startRunning blokuje, dokud kamera nenaběhne; volá se z pracovního vlákna Kotlinu.
    [session startRunning];
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_calls_MacCamera_nativeStop(JNIEnv *env, jclass cls) {
    PlumeCameraPreview *preview = cameraPreview;
    cameraPreview = nil;
    if (preview == nil) return;
    // Synchronně: po návratu je kamera volná pro knihovnu a nepřijde už žádný snímek.
    [preview.session stopRunning];
    dispatch_sync(preview.queue, ^{});
}
