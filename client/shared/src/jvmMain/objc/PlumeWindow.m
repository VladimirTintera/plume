// Titulek okna na macOS v barvách motivu aplikace, volaný z Kotlinu přes JNI
// (eu.tintera.plume.core.platform.MacTitleBar). Překládá ho spolu s notifikacemi úloha
// compileMacNative v client/shared/build.gradle.kts; viz docs/poznatky/desktop.md.
//
// Na rozdíl od notifikací funguje i v holém `java` z ladicího běhu: NSWindow má každé okno AWT.

#import <AppKit/AppKit.h>
#include <jni.h>

// Okno se hledá podle titulku: AWT k NSWindow veřejnou cestu nedává (ukazatel drží
// sun.lwawt.macosx.CPlatformWindow, kam se bez --add-opens nedosáhne) a Plume má hlavní okno
// jediné. Na NSWindow se smí sahat jen z hlavního vlákna AppKitu, ne z fronty událostí AWT.
//
// - appearance: světlý / tmavý titulek podle ZVOLENÉHO motivu (text titulku, nabídky okna).
// - titlebarAppearsTransparent + backgroundColor: titulek přebere barvu plochy okna, takže
//   splyne s aplikací. Obsah pod titulek nejde, AWT ho kreslí dál pod lištou.
JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_platform_MacTitleBar_nativeApply(JNIEnv *env, jclass cls, jstring title, jboolean dark,
                                                           jfloat red, jfloat green, jfloat blue) {
    if (title == NULL) return;
    const char *chars = (*env)->GetStringUTFChars(env, title, NULL);
    NSString *windowTitle = [NSString stringWithUTF8String:chars];
    (*env)->ReleaseStringUTFChars(env, title, chars);
    BOOL isDark = dark == JNI_TRUE;
    dispatch_async(dispatch_get_main_queue(), ^{
        NSAppearance *appearance = [NSAppearance appearanceNamed:isDark ? NSAppearanceNameDarkAqua : NSAppearanceNameAqua];
        NSColor *background = [NSColor colorWithSRGBRed:red green:green blue:blue alpha:1.0];
        for (NSWindow *window in NSApp.windows) {
            if (![window.title isEqualToString:windowTitle]) continue;
            window.appearance = appearance;
            window.titlebarAppearsTransparent = YES;
            window.backgroundColor = background;
        }
    });
}

// Hlavní okno Plume podle titulku (viz výš, proč podle titulku); volat jen z hlavního vlákna.
static NSWindow *PlumeWindowWithTitle(NSString *title) {
    for (NSWindow *window in NSApp.windows) {
        if ([window.title isEqualToString:title]) return window;
    }
    return nil;
}

static NSString *PlumeString(JNIEnv *env, jstring value) {
    const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
    NSString *result = [NSString stringWithUTF8String:chars];
    (*env)->ReleaseStringUTFChars(env, value, chars);
    return result;
}

// Tah oknem za pruh titulku. Na Macu ho začíná JEN tohle, ne `AWTWindowDragView` z JBR: ten
// rozhoduje při prvním `mouseDragged` podle odpovědi na předchozí zpracovanou událost, takže
// tah chodil jen někdy, a když tah začal on i my, okno uskočilo (DesktopTitleBar.kt).
// Volá se po zpracování události ve frontě AWT, tedy později, než přišla: bere se proto
// poslední událost aplikace (`currentEvent`), stejně jako `drag_window` v knihovně tao (Tauri).
// Okno se do té doby nehnulo, takže poloha v ní sedí. Událost z jiného okna (popup) by měla
// polohu v jiných souřadnicích a okno by uskočilo — tu ani bez stisknutého tlačítka nic.
JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_platform_MacTitleBar_nativeStartDrag(JNIEnv *env, jclass cls, jstring title) {
    if (title == NULL) return;
    NSString *windowTitle = PlumeString(env, title);
    dispatch_async(dispatch_get_main_queue(), ^{
        if (([NSEvent pressedMouseButtons] & 1) == 0) return;
        NSEvent *event = NSApp.currentEvent;
        if (event == nil) return;
        if (event.type != NSEventTypeLeftMouseDragged && event.type != NSEventTypeLeftMouseDown) return;
        NSWindow *window = PlumeWindowWithTitle(windowTitle);
        if (window == nil || event.window != window) return;
        [window performWindowDragWithEvent:event];
    });
}

// Dvojklik na pruh titulku podle nastavení systému (Plocha a Dock → „Dvojklik na titulek
// okna"), stejně jako `AWTWindowDragView` v JBR, který ho dělal, dokud o pruhu rozhodoval on.
JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_platform_MacTitleBar_nativeTitleBarDoubleClick(JNIEnv *env, jclass cls, jstring title) {
    if (title == NULL) return;
    NSString *windowTitle = PlumeString(env, title);
    dispatch_async(dispatch_get_main_queue(), ^{
        NSWindow *window = PlumeWindowWithTitle(windowTitle);
        if (window == nil) return;
        NSString *action = [[NSUserDefaults standardUserDefaults] stringForKey:@"AppleActionOnDoubleClick"];
        if (action != nil && [action caseInsensitiveCompare:@"Minimize"] == NSOrderedSame) {
            [window performMiniaturize:nil];
        } else if (action == nil || [action caseInsensitiveCompare:@"None"] != NSOrderedSame) {
            [window performZoom:nil];
        }
    });
}
