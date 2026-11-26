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

// Tah oknem, který začal v Compose (stisk na prvku v pruhu titulku a pohyb přes práh tahu),
// ne v `AWTWindowDragView` z JBR — ten o tahu rozhoduje jen jednou, při prvním `mouseDragged`,
// a stisk, který si vzal prvek, oknem nehne. Volá se po zpracování události ve frontě AWT,
// tedy později, než přišla: bere se proto poslední událost aplikace (`currentEvent`), stejně
// jako `drag_window` v knihovně tao (Tauri). Bez stisknutého tlačítka se nic nestane.
JNIEXPORT void JNICALL
Java_eu_tintera_plume_core_platform_MacTitleBar_nativeStartDrag(JNIEnv *env, jclass cls, jstring title) {
    if (title == NULL) return;
    const char *chars = (*env)->GetStringUTFChars(env, title, NULL);
    NSString *windowTitle = [NSString stringWithUTF8String:chars];
    (*env)->ReleaseStringUTFChars(env, title, chars);
    dispatch_async(dispatch_get_main_queue(), ^{
        NSEvent *event = NSApp.currentEvent;
        if (([NSEvent pressedMouseButtons] & 1) == 0) return;
        if (event == nil) return;
        if (event.type != NSEventTypeLeftMouseDragged && event.type != NSEventTypeLeftMouseDown) return;
        for (NSWindow *window in NSApp.windows) {
            if (![window.title isEqualToString:windowTitle]) continue;
            [window performWindowDragWithEvent:event];
            return;
        }
    });
}
