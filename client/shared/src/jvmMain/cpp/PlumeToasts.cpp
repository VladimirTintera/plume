// Notifikace na Windows přes toasty WinRT (Windows.UI.Notifications), volané z Kotlinu přes JNI
// (eu.tintera.plume.core.notifications.WindowsToasts). Překládá ji úloha
// compileWindowsNotifications v client/shared/build.gradle.kts; viz docs/poznatky/desktop.md.
//
// Aplikace z MSI není zabalená (MSIX), takže si identitu pro toasty zakládá sama: AppUserModelID
// na zástupci ve Startu (bez něj Windows 11 toast přijme, ale nevykreslí), v registru
// HKCU\Software\Classes\AppUserModelId a k němu COM třídu, kterou Windows zavolá po kliknutí na
// toast (INotificationActivationCallback). XML toastu skládá Kotlin, tady se jen předává systému.

#include <unknwn.h>
#include <windows.h>
#include <NotificationActivationCallback.h>
#include <propkey.h>
#include <propvarutil.h>
#include <shobjidl.h>
#include <shlobj.h>

#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Data.Xml.Dom.h>
#include <winrt/Windows.UI.Notifications.h>

#include <jni.h>

#include <future>
#include <string>
#include <thread>

using winrt::Windows::Data::Xml::Dom::XmlDocument;
using winrt::Windows::UI::Notifications::ToastNotification;
using winrt::Windows::UI::Notifications::ToastNotificationManager;

static JavaVM *plumeVm;
static jclass plumeClass;
static jmethodID plumeOnActivated;
static std::wstring plumeAumid;

static std::wstring plumeString(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const jchar *chars = env->GetStringChars(value, nullptr);
    std::wstring result(reinterpret_cast<const wchar_t *>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return result;
}

// Volání z Javy přichází z různých vláken. Bez COM se WinRT zavolat nedá; MTA drží naživu vlákno
// aktivátoru, takže by stačilo i implicitní, ale vlákno se přihlásí výslovně. Vlákno ve STA
// (AWT) vrátí RPC_E_CHANGED_MODE a zůstane ve STA — objekty toastů jsou agilní, nevadí to.
static void plumeEnsureCom() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
}

static jstring plumeJavaString(JNIEnv *env, LPCWSTR value) {
    return value == nullptr ? nullptr : env->NewString(reinterpret_cast<const jchar *>(value), static_cast<jsize>(wcslen(value)));
}

// Klik na toast nebo jeho tlačítko: Windows zavolá aktivátor na vlákně RPC s argumenty z XML
// (`launch` toastu, `arguments` tlačítka) a s tím, co uživatel napsal do pole `reply`.
static void plumeDeliver(LPCWSTR arguments, LPCWSTR reply) {
    if (plumeVm == nullptr || arguments == nullptr) return;
    JNIEnv *env = nullptr;
    bool attached = false;
    if (plumeVm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) == JNI_EDETACHED) {
        if (plumeVm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&env), nullptr) != JNI_OK) return;
        attached = true;
    }
    jstring value = plumeJavaString(env, arguments);
    jstring text = plumeJavaString(env, reply);
    env->CallStaticVoidMethod(plumeClass, plumeOnActivated, value, text);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(value);
    if (text != nullptr) env->DeleteLocalRef(text);
    if (attached) plumeVm->DetachCurrentThread();
}

struct PlumeActivator : winrt::implements<PlumeActivator, INotificationActivationCallback> {
    HRESULT __stdcall Activate(LPCWSTR, LPCWSTR invokedArgs, const NOTIFICATION_USER_INPUT_DATA *data, ULONG count) noexcept final {
        LPCWSTR reply = nullptr;
        for (ULONG i = 0; i < count; i++) {
            if (data[i].Key != nullptr && wcscmp(data[i].Key, L"reply") == 0) reply = data[i].Value;
        }
        plumeDeliver(invokedArgs, reply);
        return S_OK;
    }
};

struct PlumeActivatorFactory : winrt::implements<PlumeActivatorFactory, IClassFactory> {
    HRESULT __stdcall CreateInstance(IUnknown *outer, GUID const &iid, void **result) noexcept final {
        *result = nullptr;
        if (outer != nullptr) return CLASS_E_NOAGGREGATION;
        try {
            return winrt::make<PlumeActivator>()->QueryInterface(iid, result);
        } catch (...) {
            return winrt::to_hresult();
        }
    }

    HRESULT __stdcall LockServer(BOOL) noexcept final { return S_OK; }
};

static LSTATUS plumeSetValue(const std::wstring &key, LPCWSTR name, const std::wstring &value) {
    return RegSetKeyValueW(HKEY_CURRENT_USER, key.c_str(), name, REG_SZ, value.c_str(),
                           static_cast<DWORD>((value.size() + 1) * sizeof(wchar_t)));
}

// Zástupce ve Startu s AUMID a CLSID aktivátoru. Bez něj Windows 11 toast přijme do historie,
// ale nevykreslí ho (ověřeno: identita jen v registru se neukázala, se zástupcem ano).
// Existujícímu zástupci (z MSI) se jen doplní vlastnosti a cíl zůstává; chybějící se založí
// s cílem `target`. Zapisuje se, jen když se něco liší — Průzkumník po zápisu zástupce indexuje.
static HRESULT plumeEnsureShortcut(const std::wstring &path, const std::wstring &target, const std::wstring &aumid,
                                   const CLSID &activator) {
    try {
        auto link = winrt::create_instance<IShellLinkW>(CLSID_ShellLink, CLSCTX_INPROC_SERVER);
        auto file = link.as<IPersistFile>();
        const bool exists = GetFileAttributesW(path.c_str()) != INVALID_FILE_ATTRIBUTES;
        if (exists) {
            winrt::check_hresult(file->Load(path.c_str(), STGM_READWRITE));
        } else {
            winrt::check_hresult(link->SetPath(target.c_str()));
        }
        auto store = link.as<IPropertyStore>();
        bool changed = !exists;
        PROPVARIANT value;
        PropVariantInit(&value);
        if (SUCCEEDED(store->GetValue(PKEY_AppUserModel_ID, &value))) {
            changed |= value.vt != VT_LPWSTR || aumid != value.pwszVal;
        }
        PropVariantClear(&value);
        if (SUCCEEDED(store->GetValue(PKEY_AppUserModel_ToastActivatorCLSID, &value))) {
            changed |= value.vt != VT_CLSID || !IsEqualCLSID(*value.puuid, activator);
        }
        PropVariantClear(&value);
        if (!changed) return S_OK;

        winrt::check_hresult(InitPropVariantFromString(aumid.c_str(), &value));
        HRESULT hr = store->SetValue(PKEY_AppUserModel_ID, value);
        PropVariantClear(&value);
        winrt::check_hresult(hr);
        winrt::check_hresult(InitPropVariantFromCLSID(activator, &value));
        hr = store->SetValue(PKEY_AppUserModel_ToastActivatorCLSID, value);
        PropVariantClear(&value);
        winrt::check_hresult(hr);
        winrt::check_hresult(store->Commit());
        return file->Save(path.c_str(), TRUE);
    } catch (...) {
        return winrt::to_hresult();
    }
}

// Registrace COM třídy musí přežít vlákno, které ji založilo: třída zaregistrovaná v MTA platí,
// dokud MTA existuje, a to drží tohle vlákno, které už nikdy neskončí.
static HRESULT plumeRegisterActivator(const CLSID &clsid) {
    std::promise<HRESULT> registered;
    auto result = registered.get_future();
    std::thread([clsid, &registered] {
        CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        DWORD cookie = 0;
        HRESULT hr;
        try {
            auto factory = winrt::make<PlumeActivatorFactory>();
            hr = CoRegisterClassObject(clsid, factory.get(), CLSCTX_LOCAL_SERVER, REGCLS_MULTIPLEUSE, &cookie);
            if (SUCCEEDED(hr)) factory.detach();
        } catch (...) {
            hr = winrt::to_hresult();
        }
        registered.set_value(hr);
        if (FAILED(hr)) return;
        HANDLE forever = CreateEventW(nullptr, TRUE, FALSE, nullptr);
        WaitForSingleObject(forever, INFINITE);
    }).detach();
    return result.get();
}

// Identita aplikace: zástupce ve Startu, AUMID procesu, registr a aktivátor. `serverCommand` je
// příkaz, kterým Windows aplikaci spustí po kliknutí na toast, když neběží (null = ladicí běh
// z `java`, jen za běhu). Volat dřív, než vznikne první okno — AUMID procesu se oknu přiřadí
// při vzniku. Vrací HRESULT; 0 = v pořádku.
extern "C" JNIEXPORT jint JNICALL
Java_eu_tintera_plume_core_notifications_WindowsToasts_nativeInit(
    JNIEnv *env, jclass cls, jstring aumid, jstring displayName, jstring iconPath, jstring clsid, jstring serverCommand,
    jstring shortcutPath, jstring shortcutTarget, jboolean processAumid) {
    env->GetJavaVM(&plumeVm);
    plumeClass = static_cast<jclass>(env->NewGlobalRef(cls));
    plumeOnActivated = env->GetStaticMethodID(cls, "onActivated", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (plumeOnActivated == nullptr) return E_FAIL;
    plumeAumid = plumeString(env, aumid);
    const std::wstring clsidText = plumeString(env, clsid);

    CLSID activator;
    HRESULT hr = CLSIDFromString(clsidText.c_str(), &activator);
    if (FAILED(hr)) return hr;

    plumeEnsureCom();
    hr = plumeEnsureShortcut(plumeString(env, shortcutPath), plumeString(env, shortcutTarget), plumeAumid, activator);
    if (FAILED(hr)) return hr;
    // Zástupce nese AUMID, takže ho musí nést i okno — jinak by se na hlavním panelu neslo pod
    // jinou identitou než připnutá ikona a ukázalo by se dvakrát. Ladicí běh ne: jeho zástupce
    // míří na java.exe a hlavní panel by oknu se zástupcovým AUMID dal ikonu Javy. Toasty se
    // vykreslí i bez AUMID procesu, stačí jim zástupce.
    if (processAumid) {
        hr = SetCurrentProcessExplicitAppUserModelID(plumeAumid.c_str());
        if (FAILED(hr)) return hr;
    }

    const std::wstring appKey = L"Software\\Classes\\AppUserModelId\\" + plumeAumid;
    LSTATUS status = plumeSetValue(appKey, L"DisplayName", plumeString(env, displayName));
    if (status == ERROR_SUCCESS && iconPath != nullptr) status = plumeSetValue(appKey, L"IconUri", plumeString(env, iconPath));
    if (status == ERROR_SUCCESS) status = plumeSetValue(appKey, L"CustomActivator", clsidText);
    if (status == ERROR_SUCCESS && serverCommand != nullptr) {
        status = plumeSetValue(L"Software\\Classes\\CLSID\\" + clsidText + L"\\LocalServer32", nullptr,
                               plumeString(env, serverCommand));
    }
    if (status != ERROR_SUCCESS) return HRESULT_FROM_WIN32(status);

    return plumeRegisterActivator(activator);
}

static bool plumeRegistryOff(const std::wstring &key, LPCWSTR name) {
    DWORD value = 1;
    DWORD size = sizeof(value);
    LSTATUS status = RegGetValueW(HKEY_CURRENT_USER, key.c_str(), name, RRF_RT_REG_DWORD, nullptr, &value, &size);
    return status == ERROR_SUCCESS && value == 0;
}

// 0 = povoleno, 1 = vypnuto pro aplikaci, 2 = vypnutá všechna oznámení.
//
// ToastNotifier.Setting u aplikace bez zástupce ve Startu NEFUNGUJE (vrací ERROR_NOT_FOUND,
// ověřeno na Windows 11), takže se čte totéž, co zapisuje Nastavení → Systém → Oznámení.
extern "C" JNIEXPORT jint JNICALL
Java_eu_tintera_plume_core_notifications_WindowsToasts_nativeSetting(JNIEnv *, jclass) {
    if (plumeRegistryOff(L"Software\\Microsoft\\Windows\\CurrentVersion\\PushNotifications", L"ToastEnabled")) return 2;
    if (plumeRegistryOff(L"Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings\\" + plumeAumid, L"Enabled")) return 1;
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_eu_tintera_plume_core_notifications_WindowsToasts_nativeShow(JNIEnv *env, jclass, jstring xml, jstring tag, jstring group) {
    plumeEnsureCom();
    try {
        XmlDocument document;
        document.LoadXml(plumeString(env, xml));
        ToastNotification toast(document);
        toast.Tag(plumeString(env, tag));
        toast.Group(plumeString(env, group));
        ToastNotificationManager::CreateToastNotifier(plumeAumid).Show(toast);
        return S_OK;
    } catch (...) {
        return winrt::to_hresult();
    }
}

// Nezabalená aplikace musí u historie uvádět AUMID; přetížení bez něj platí jen pro MSIX.
extern "C" JNIEXPORT jint JNICALL
Java_eu_tintera_plume_core_notifications_WindowsToasts_nativeRemove(JNIEnv *env, jclass, jobjectArray tags, jstring group) {
    plumeEnsureCom();
    try {
        auto history = ToastNotificationManager::History();
        const std::wstring groupText = plumeString(env, group);
        const jsize count = env->GetArrayLength(tags);
        for (jsize i = 0; i < count; i++) {
            auto tag = static_cast<jstring>(env->GetObjectArrayElement(tags, i));
            history.Remove(plumeString(env, tag), groupText, plumeAumid);
            env->DeleteLocalRef(tag);
        }
        return S_OK;
    } catch (...) {
        return winrt::to_hresult();
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_eu_tintera_plume_core_notifications_WindowsToasts_nativeRemoveAll(JNIEnv *, jclass) {
    plumeEnsureCom();
    try {
        ToastNotificationManager::History().Clear(plumeAumid);
        return S_OK;
    } catch (...) {
        return winrt::to_hresult();
    }
}
