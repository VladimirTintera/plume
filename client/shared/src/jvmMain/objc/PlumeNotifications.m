// Notifikace na macOS přes UNUserNotificationCenter, volané z Kotlinu přes JNI
// (eu.tintera.plume.core.notifications.MacNotificationCenter). Překládá ji úloha
// compileMacNotifications v client/shared/build.gradle.kts; viz docs/poznatky/desktop.md.
//
// UNUserNotificationCenter funguje JEN v procesu s vlastním .app balíčkem: v holém `java`
// (ladicí běh z Gradle) vyhodí NSInternalInconsistencyException „bundleProxyForCurrentProcess
// is nil" a shodí celý proces. Proto se každé volání napřed ptá na plumeAvailable().

#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>
#include <jni.h>

static JavaVM *plumeVm;
static jclass plumeClass;
static jmethodID plumeOnActivated;

static NSString *const KEY_CHAT_ID = @"chatId";
static NSString *const KEY_TOPIC_ID = @"topicId";
static NSString *const KEY_CALL_ID = @"callId";

// Kategorie oznámení příchozího hovoru a její tlačítka; identifikátory tlačítek zná i Kotlin
// (MacNotificationCenter.ACTION_*).
static NSString *const CATEGORY_CALL = @"call";
static NSString *const ACTION_ANSWER = @"answer";
static NSString *const ACTION_DECLINE = @"decline";

static BOOL plumeAvailable(void) {
    return [[[[NSBundle mainBundle] bundleURL] pathExtension] isEqualToString:@"app"];
}

static NSString *plumeString(JNIEnv *env, jstring value) {
    if (value == NULL) return nil;
    const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
    NSString *result = [NSString stringWithUTF8String:chars];
    (*env)->ReleaseStringUTFChars(env, value, chars);
    return result;
}

@interface PlumeNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end

@implementation PlumeNotificationDelegate

// Bez delegáta systém notifikaci aplikace v popředí neukáže vůbec. Jestli ji ukázat, rozhoduje
// presenter v Kotlinu (aktivní okno notifikaci nedostane), sem už jde jen to, co se ukázat má.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    completionHandler(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList |
                      UNNotificationPresentationOptionSound);
}

// Klik na notifikaci: systém aplikaci aktivuje sám, Kotlin otevře chat a vytáhne okno.
// Tlačítko hovoru přijde jako jeho identifikátor; Přijmout aplikaci aktivuje (volba
// Foreground), Odmítnout běží na pozadí.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))completionHandler {
    NSDictionary *info = response.notification.request.content.userInfo;
    NSNumber *chatId = info[KEY_CHAT_ID];
    NSNumber *topicId = info[KEY_TOPIC_ID];
    NSNumber *callId = info[KEY_CALL_ID];
    NSString *action = response.actionIdentifier;
    BOOL button = [action isEqualToString:ACTION_ANSWER] || [action isEqualToString:ACTION_DECLINE];
    if (chatId != nil && plumeVm != NULL) {
        JNIEnv *env = NULL;
        BOOL attached = NO;
        if ((*plumeVm)->GetEnv(plumeVm, (void **)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
            (*plumeVm)->AttachCurrentThreadAsDaemon(plumeVm, (void **)&env, NULL);
            attached = YES;
        }
        jstring jaction = button ? (*env)->NewStringUTF(env, action.UTF8String) : NULL;
        (*env)->CallStaticVoidMethod(env, plumeClass, plumeOnActivated,
                                     (jlong)chatId.longLongValue, (jint)(topicId != nil ? topicId.intValue : 0),
                                     jaction, (jint)(callId != nil ? callId.intValue : 0));
        if (jaction != NULL) (*env)->DeleteLocalRef(env, jaction);
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        if (attached) (*plumeVm)->DetachCurrentThread(plumeVm);
    }
    completionHandler();
}

@end

static PlumeNotificationDelegate *plumeDelegate;

JNIEXPORT jboolean JNICALL
Java_eu_tintera_plume_core_notifications_MacNotificationCenter_nativeInit(JNIEnv *env, jclass cls) {
    if (!plumeAvailable()) return JNI_FALSE;
    (*env)->GetJavaVM(env, &plumeVm);
    plumeClass = (*env)->NewGlobalRef(env, cls);
    plumeOnActivated = (*env)->GetStaticMethodID(env, cls, "onActivated", "(JILjava/lang/String;I)V");
    if (plumeOnActivated == NULL) return JNI_FALSE;
    plumeDelegate = [PlumeNotificationDelegate new];
    UNUserNotificationCenter.currentNotificationCenter.delegate = plumeDelegate;
    return JNI_TRUE;
}

// 0 = nerozhodnuto, 1 = povoleno, 2 = zakázáno. Blokuje nejvýš sekundu: completion handler
// běží na frontě UserNotifications, ne na hlavním vlákně, takže se čekání nezablokuje samo.
JNIEXPORT jint JNICALL
Java_eu_tintera_plume_core_notifications_MacNotificationCenter_nativeAuthorizationStatus(JNIEnv *env, jclass cls) {
    if (!plumeAvailable()) return 2;
    __block jint result = 0;
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    [UNUserNotificationCenter.currentNotificationCenter getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
        switch (settings.authorizationStatus) {
            case UNAuthorizationStatusAuthorized:
            case UNAuthorizationStatusProvisional:
                result = 1;
                break;
            case UNAuthorizationStatusDenied:
                result = 2;
                break;
            default:
                result = 0;
        }
        dispatch_semaphore_signal(done);
    }];
    dispatch_semaphore_wait(done, dispatch_time(DISPATCH_TIME_NOW, NSEC_PER_SEC));
    return result;
}

// Systémový dotaz. Čeká na odpověď uživatele — volá se mimo hlavní vlákno a mimo AWT.
JNIEXPORT jboolean JNICALL
Java_eu_tintera_plume_core_notifications_MacNotificationCenter_nativeRequestAuthorization(JNIEnv *env, jclass cls) {
    if (!plumeAvailable()) return JNI_FALSE;
    __block BOOL result = NO;
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    UNAuthorizationOptions options = UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge;
    [UNUserNotificationCenter.currentNotificationCenter requestAuthorizationWithOptions:options
                                                                       completionHandler:^(BOOL granted, NSError *error) {
        result = granted;
        dispatch_semaphore_signal(done);
    }];
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_notifications_MacNotificationCenter_nativeShow(
    JNIEnv *env, jclass cls, jstring identifier, jstring thread, jstring title, jstring subtitle,
    jstring body, jlong chatId, jint topicId, jboolean silent, jstring attachmentPath, jint callId) {
    if (!plumeAvailable()) return;
    UNMutableNotificationContent *content = [UNMutableNotificationContent new];
    content.title = plumeString(env, title) ?: @"";
    NSString *sub = plumeString(env, subtitle);
    if (sub != nil) content.subtitle = sub;
    content.body = plumeString(env, body) ?: @"";
    content.threadIdentifier = plumeString(env, thread) ?: @"";
    content.userInfo = @{KEY_CHAT_ID: @(chatId), KEY_TOPIC_ID: @(topicId), KEY_CALL_ID: @(callId)};
    if (!silent) content.sound = UNNotificationSound.defaultSound;
    if (callId != 0) content.categoryIdentifier = CATEGORY_CALL;

    // Příloha je KOPIE (dělá ji Kotlin): systém soubor přílohy přesouvá do vlastního úložiště.
    NSString *path = plumeString(env, attachmentPath);
    if (path != nil) {
        NSError *error = nil;
        UNNotificationAttachment *attachment =
            [UNNotificationAttachment attachmentWithIdentifier:@"avatar" URL:[NSURL fileURLWithPath:path] options:nil error:&error];
        if (attachment != nil) content.attachments = @[attachment];
    }

    UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:plumeString(env, identifier)
                                                                          content:content
                                                                          trigger:nil];
    [UNUserNotificationCenter.currentNotificationCenter addNotificationRequest:request withCompletionHandler:nil];
}

// Kategorie se registruje asynchronně a celá sada naráz (jiné kategorie Plume nemá); oznámení
// přidané dřív, než doběhne, by tlačítka nemělo — proto ji Kotlin nastavuje předem.
JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_notifications_MacNotificationCenter_nativeSetCallActions(
    JNIEnv *env, jclass cls, jstring answer, jstring decline) {
    if (!plumeAvailable()) return;
    UNNotificationAction *answerAction = [UNNotificationAction actionWithIdentifier:ACTION_ANSWER
                                                                              title:plumeString(env, answer) ?: @""
                                                                            options:UNNotificationActionOptionForeground];
    UNNotificationAction *declineAction = [UNNotificationAction actionWithIdentifier:ACTION_DECLINE
                                                                               title:plumeString(env, decline) ?: @""
                                                                             options:UNNotificationActionOptionDestructive];
    UNNotificationCategory *category = [UNNotificationCategory categoryWithIdentifier:CATEGORY_CALL
                                                                              actions:@[answerAction, declineAction]
                                                                    intentIdentifiers:@[]
                                                                              options:UNNotificationCategoryOptionNone];
    [UNUserNotificationCenter.currentNotificationCenter setNotificationCategories:[NSSet setWithObject:category]];
}

JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_notifications_MacNotificationCenter_nativeRemove(JNIEnv *env, jclass cls, jobjectArray identifiers) {
    if (!plumeAvailable()) return;
    jsize count = (*env)->GetArrayLength(env, identifiers);
    NSMutableArray<NSString *> *ids = [NSMutableArray arrayWithCapacity:count];
    for (jsize i = 0; i < count; i++) {
        jstring value = (jstring)(*env)->GetObjectArrayElement(env, identifiers, i);
        NSString *string = plumeString(env, value);
        if (string != nil) [ids addObject:string];
        (*env)->DeleteLocalRef(env, value);
    }
    UNUserNotificationCenter *center = UNUserNotificationCenter.currentNotificationCenter;
    [center removeDeliveredNotificationsWithIdentifiers:ids];
    [center removePendingNotificationRequestsWithIdentifiers:ids];
}

JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_notifications_MacNotificationCenter_nativeRemoveAll(JNIEnv *env, jclass cls) {
    if (!plumeAvailable()) return;
    UNUserNotificationCenter *center = UNUserNotificationCenter.currentNotificationCenter;
    [center removeAllDeliveredNotifications];
    [center removeAllPendingNotificationRequests];
}
