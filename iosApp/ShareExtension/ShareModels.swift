import Foundation

// Formát sdíleného kontejneru App Group. MUSÍ sedět s `ShareTargets.ios.kt` v aplikaci:
//
//   share/targets.json               snímek chatů pro výběr (píše aplikace)
//   share/avatars/<chat>-<file>.jpg  profilovky ke snímku
//   share/inbox/<id>/manifest.json   co a kam sdílet (píše rozšíření, POSLEDNÍ)
//   share/inbox/<id>/<soubory>

struct ShareSnapshot: Decodable {
    let chats: [ShareSnapshotChat]
}

struct ShareSnapshotChat: Decodable, Identifiable, Hashable {
    let id: Int64
    let title: String
    /// Jméno souboru v `share/avatars`, nebo `nil`, když profilovka není stažená.
    let avatar: String?
}

struct ShareManifest: Encodable {
    let chatId: Int64
    let text: String?
    let items: [ShareManifestItem]
}

/// `kind`: `image`, `video`, nebo `file`. `file` je jméno souboru ve složce sdílení.
struct ShareManifestItem: Encodable {
    let file: String
    let kind: String
}

enum ShareStore {
    /// Adresář `share` v kontejneru App Group; `nil`, když skupina v podpisu chybí.
    static var directory: URL? {
        guard
            let group = Bundle.main.object(forInfoDictionaryKey: "PlumeAppGroup") as? String,
            let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)
        else { return nil }
        return container.appendingPathComponent("share", isDirectory: true)
    }

    static func loadSnapshot() -> ShareSnapshot? {
        guard
            let url = directory?.appendingPathComponent("targets.json"),
            let data = try? Data(contentsOf: url)
        else { return nil }
        return try? JSONDecoder().decode(ShareSnapshot.self, from: data)
    }

    static func avatarURL(_ name: String) -> URL? {
        directory?.appendingPathComponent("avatars", isDirectory: true).appendingPathComponent(name)
    }

    /// Nová složka pro jedno sdílení. Jméno začíná časem, aby aplikace vyřizovala v pořadí.
    static func newInboxFolder() throws -> URL {
        guard let directory else { throw ShareError.noContainer }
        let name = "\(Int64(Date().timeIntervalSince1970 * 1000))-\(UUID().uuidString)"
        let folder = directory.appendingPathComponent("inbox", isDirectory: true).appendingPathComponent(name, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    /// Manifest se zapisuje POSLEDNÍ a atomicky — aplikace sdílení bez něj nevyzvedne.
    static func writeManifest(_ manifest: ShareManifest, to folder: URL) throws {
        let data = try JSONEncoder().encode(manifest)
        try data.write(to: folder.appendingPathComponent("manifest.json"), options: .atomic)
    }
}

enum ShareError: Error {
    case noContainer
}
