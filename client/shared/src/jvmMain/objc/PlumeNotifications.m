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

// Argumenty oznámení (`chat=…&topic=…&msg=…`), tytéž jako u toastu na Windows; rozebírá je
// Kotlin (parseActivation). Most jim nerozumí, jen je vrátí s identifikátorem tlačítka.
static NSString *const KEY_ARGUMENTS = @"arguments";
// Oznámení ze starší verze nesla chat, téma a hovor zvlášť; v Centru oznámení můžou zůstat.
static NSString *const KEY_CHAT_ID = @"chatId";
static NSString *const KEY_TOPIC_ID = @"topicId";
static NSString *const KEY_CALL_ID = @"callId";

// Kategorie a identifikátory tlačítek zná i Kotlin (MacNotificationCenter.CATEGORY_*, ACTION_*).
// Tlačítka zpráv se liší podle toho, jestli jde odpovědět a ztlumit, proto čtyři kategorie.
static NSString *const CATEGORY_CALL = @"call";
static NSString *const CATEGORY_MESSAGE = @"message";
static NSString *const CATEGORY_MESSAGE_REPLY = @"message-reply";
static NSString *const CATEGORY_MESSAGE_MUTE = @"message-mute";
static NSString *const CATEGORY_MESSAGE_REPLY_MUTE = @"message-reply-mute";
static NSString *const ACTION_ANSWER = @"answer";
static NSString *const ACTION_DECLINE = @"decline";
static NSString *const ACTION_REPLY = @"reply";
static NSString *const ACTION_READ = @"read";
static NSString *const ACTION_MUTE = @"mute";

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

static NSString *plumeArguments(NSDictionary *info) {
    NSString *arguments = info[KEY_ARGUMENTS];
    if (arguments != nil) return arguments;
    NSNumber *chatId = info[KEY_CHAT_ID];
    if (chatId == nil) return nil;
    NSNumber *topicId = info[KEY_TOPIC_ID];
    NSNumber *callId = info[KEY_CALL_ID];
    return [NSString stringWithFormat:@"chat=%lld&topic=%d&call=%d", chatId.longLongValue,
                                      topicId != nil ? topicId.intValue : 0, callId != nil ? callId.intValue : 0];
}

static jstring plumeJString(JNIEnv *env, NSString *value) {
    return value != nil ? (*env)->NewStringUTF(env, value.UTF8String) : NULL;
}

// Klik na notifikaci: systém aplikaci aktivuje sám, Kotlin otevře chat a vytáhne okno.
// Tlačítko přijde jako jeho identifikátor, u odpovědi i s napsaným textem. Přijmout hovor
// aplikaci aktivuje (volba Foreground), ostatní tlačítka běží na pozadí.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))completionHandler {
    NSString *arguments = plumeArguments(response.notification.request.content.userInfo);
    NSString *action = response.actionIdentifier;
    // Zavření oznámení systém hlásí jen kategoriím s CustomDismissAction; Plume žádnou nemá.
    BOOL dismissed = [action isEqualToString:UNNotificationDismissActionIdentifier];
    if ([action isEqualToString:UNNotificationDefaultActionIdentifier]) action = nil;
    NSString *text = [response isKindOfClass:UNTextInputNotificationResponse.class]
                         ? ((UNTextInputNotificationResponse *)response).userText
                         : nil;
    if (arguments != nil && !dismissed && plumeVm != NULL) {
        JNIEnv *env = NULL;
        BOOL attached = NO;
        if ((*plumeVm)->GetEnv(plumeVm, (void **)&env, JNI_VERSION_1_8) == JNI_EDETACHED) {
            (*plumeVm)->AttachCurrentThreadAsDaemon(plumeVm, (void **)&env, NULL);
            attached = YES;
        }
        jstring jarguments = plumeJString(env, arguments);
        jstring jaction = plumeJString(env, action);
        jstring jtext = plumeJString(env, text);
        (*env)->CallStaticVoidMethod(env, plumeClass, plumeOnActivated, jarguments, jaction, jtext);
        if (jarguments != NULL) (*env)->DeleteLocalRef(env, jarguments);
        if (jaction != NULL) (*env)->DeleteLocalRef(env, jaction);
        if (jtext != NULL) (*env)->DeleteLocalRef(env, jtext);
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
    plumeOnActivated = (*env)->GetStaticMethodID(env, cls, "onActivated",
                                                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
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
    jstring body, jstring arguments, jstring category, jboolean silent, jstring attachmentPath) {
    if (!plumeAvailable()) return;
    UNMutableNotificationContent *content = [UNMutableNotificationContent new];
    content.title = plumeString(env, title) ?: @"";
    NSString *sub = plumeString(env, subtitle);
    if (sub != nil) content.subtitle = sub;
    content.body = plumeString(env, body) ?: @"";
    content.threadIdentifier = plumeString(env, thread) ?: @"";
    content.userInfo = @{KEY_ARGUMENTS: plumeString(env, arguments) ?: @""};
    if (!silent) content.sound = UNNotificationSound.defaultSound;
    NSString *categoryIdentifier = plumeString(env, category);
    if (categoryIdentifier != nil) content.categoryIdentifier = categoryIdentifier;

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

static UNNotificationCategory *plumeCategory(NSString *identifier, NSArray<UNNotificationAction *> *actions) {
    return [UNNotificationCategory categoryWithIdentifier:identifier
                                                  actions:actions
                                        intentIdentifiers:@[]
                                                  options:UNNotificationCategoryOptionNone];
}

// Kategorie se registrují asynchronně a celá sada naráz (setNotificationCategories přepíše
// všechny); oznámení přidané dřív, než to doběhne, by tlačítka nemělo — proto je Kotlin
// nastavuje předem. Popisek NULL = ta skupina tlačítek se neregistruje (hovory tu nejdou
// přijmout, nebo aplikace akce zpráv nemá).
JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_notifications_MacNotificationCenter_nativeSetActions(
    JNIEnv *env, jclass cls, jstring answer, jstring decline, jstring reply, jstring send,
    jstring markRead, jstring mute) {
    if (!plumeAvailable()) return;
    NSMutableSet<UNNotificationCategory *> *categories = [NSMutableSet set];

    NSString *answerTitle = plumeString(env, answer);
    NSString *declineTitle = plumeString(env, decline);
    if (answerTitle != nil && declineTitle != nil) {
        UNNotificationAction *answerAction = [UNNotificationAction actionWithIdentifier:ACTION_ANSWER
                                                                                  title:answerTitle
                                                                                options:UNNotificationActionOptionForeground];
        UNNotificationAction *declineAction = [UNNotificationAction actionWithIdentifier:ACTION_DECLINE
                                                                                   title:declineTitle
                                                                                 options:UNNotificationActionOptionDestructive];
        [categories addObject:plumeCategory(CATEGORY_CALL, @[answerAction, declineAction])];
    }

    NSString *replyTitle = plumeString(env, reply);
    NSString *sendTitle = plumeString(env, send);
    NSString *markReadTitle = plumeString(env, markRead);
    NSString *muteTitle = plumeString(env, mute);
    if (replyTitle != nil && sendTitle != nil && markReadTitle != nil && muteTitle != nil) {
        // Bez volby Foreground: odpověď, přečtení i ztlumení běží na pozadí a okno se nevytahuje.
        UNNotificationAction *replyAction = [UNTextInputNotificationAction actionWithIdentifier:ACTION_REPLY
                                                                                          title:replyTitle
                                                                                        options:UNNotificationActionOptionNone
                                                                           textInputButtonTitle:sendTitle
                                                                           textInputPlaceholder:replyTitle];
        UNNotificationAction *readAction = [UNNotificationAction actionWithIdentifier:ACTION_READ
                                                                                title:markReadTitle
                                                                              options:UNNotificationActionOptionNone];
        UNNotificationAction *muteAction = [UNNotificationAction actionWithIdentifier:ACTION_MUTE
                                                                                title:muteTitle
                                                                              options:UNNotificationActionOptionNone];
        [categories addObject:plumeCategory(CATEGORY_MESSAGE, @[readAction])];
        [categories addObject:plumeCategory(CATEGORY_MESSAGE_REPLY, @[replyAction, readAction])];
        [categories addObject:plumeCategory(CATEGORY_MESSAGE_MUTE, @[readAction, muteAction])];
        [categories addObject:plumeCategory(CATEGORY_MESSAGE_REPLY_MUTE, @[replyAction, readAction, muteAction])];
    }

    [UNUserNotificationCenter.currentNotificationCenter setNotificationCategories:categories];
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
