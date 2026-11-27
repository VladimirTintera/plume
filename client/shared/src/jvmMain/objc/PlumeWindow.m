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

// Stav tahu oknem; jen z hlavního vlákna. `pendingDragWindow` čeká na nejbližší tah myší,
// `movingWindow` se právě posouvá; `downLocation` a `downOrigin` jsou z posledního stisku
// (poloha myši a okna na obrazovce), aby se okno chytilo tam, kde byl stisk.
static NSWindow *pendingDragWindow = nil;
static NSWindow *movingWindow = nil;
static NSWindow *downWindow = nil;
static NSPoint downLocation;
static NSPoint downOrigin;
static id dragMonitor = nil;

// Posune okno tak, aby místo stisku zůstalo pod myší. Titulek nesmí pod lištu nabídek
// obrazovky, nad kterou myš je (jako u systémového tahu).
static void PlumeMoveWindowWithMouse(NSWindow *window) {
    NSPoint mouse = [NSEvent mouseLocation];
    NSRect frame = window.frame;
    frame.origin = NSMakePoint(downOrigin.x + mouse.x - downLocation.x, downOrigin.y + mouse.y - downLocation.y);
    for (NSScreen *screen in NSScreen.screens) {
        if (!NSMouseInRect(mouse, screen.frame, NO)) continue;
        CGFloat top = NSMaxY(screen.visibleFrame);
        if (NSMaxY(frame) > top) frame.origin.y = top - frame.size.height;
        break;
    }
    [window setFrameOrigin:frame.origin];
}

// Hlídač událostí tahu oknem. Instaluje se předem (`nativeInstallDragMonitor` při zobrazení
// okna), ne až s prvním tahem: jinak by mu chyběl stisk, kterým první tah začal.
static void PlumeInstallDragMonitor(void) {
    if (dragMonitor != nil) return;
    NSEventMask mask = NSEventMaskLeftMouseDragged | NSEventMaskLeftMouseDown | NSEventMaskLeftMouseUp;
    dragMonitor = [NSEvent addLocalMonitorForEventsMatchingMask:mask handler:^NSEvent *(NSEvent *event) {
        switch (event.type) {
            case NSEventTypeLeftMouseDown:
                pendingDragWindow = nil;
                movingWindow = nil;
                downWindow = event.window;
                downLocation = [NSEvent mouseLocation];
                downOrigin = event.window.frame.origin;
                break;
            case NSEventTypeLeftMouseDragged:
                if (pendingDragWindow != nil && event.window == pendingDragWindow) movingWindow = pendingDragWindow;
                pendingDragWindow = nil;
                if (movingWindow != nil) PlumeMoveWindowWithMouse(movingWindow);
                break;
            default:
                pendingDragWindow = nil;
                movingWindow = nil;
                break;
        }
        return event;
    }];
}

JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_platform_MacTitleBar_nativeInstallDragMonitor(JNIEnv *env, jclass cls) {
    dispatch_async(dispatch_get_main_queue(), ^{ PlumeInstallDragMonitor(); });
}

// Tah oknem za pruh titulku. Na Macu ho začíná JEN tohle, ne `AWTWindowDragView` z JBR: ten
// rozhoduje při prvním `mouseDragged` podle odpovědi na předchozí zpracovanou událost, takže
// tah chodil jen někdy, a když tah začal on i my, okno uskočilo (DesktopTitleBar.kt).
//
// Okno posouvá tenhle kód sám, při každém `LeftMouseDragged` (lokální hlídač `NSEvent` na
// hlavním vlákně), ne systémový tah `performWindowDragWithEvent`. Ten se na trackpadu chytil
// jen ob jeden tah a okno pokaždé poskočilo — kotvu si vzal ze stisku PŘEDCHOZÍHO tahu oknem
// (náš stisk pro něj byl obsah), přestože dostal zpracovávaný tah myši v našem okně (ověřeno
// logem z tahů uživatele). Myší z computer-use fungoval pokaždé. Vlastní posun nemá
// přichytávání ke krajům ani přetažení na jinou plochu, zato dělá jen to, co myš.
// Rozhodnutí přichází z Compose až po zpracování události ve frontě AWT; okno se proto chytí
// v místě stisku (zapamatovaném hlídačem) a s nejbližším tahem myši dojede pod kurzor.
// Puštění tah ukončí, stisk čekající tah zruší.
JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_platform_MacTitleBar_nativeStartDrag(JNIEnv *env, jclass cls, jstring title) {
    if (title == NULL) return;
    NSString *windowTitle = PlumeString(env, title);
    dispatch_async(dispatch_get_main_queue(), ^{
        PlumeInstallDragMonitor();
        NSWindow *window = PlumeWindowWithTitle(windowTitle);
        if (([NSEvent pressedMouseButtons] & 1) == 0 || window == nil || window != downWindow) {
            return;
        }
        pendingDragWindow = window;
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
