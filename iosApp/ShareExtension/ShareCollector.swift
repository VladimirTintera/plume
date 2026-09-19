import Foundation
import UIKit
import UniformTypeIdentifiers

/// Obsah sdílení vytažený z `NSExtensionItem`: text a soubory zkopírované do složky sdílení.
struct CollectedShare {
    var text: String?
    var items: [ShareManifestItem]
}

/// Zkopíruje sdílené soubory do [folder] a posbírá text a odkazy.
///
/// Soubory z `loadFileRepresentation` platí **jen uvnitř callbacku** — systém je hned potom
/// maže, takže se kopírují ještě v něm. Fotka se nezmenšuje: rozšíření má přísný limit paměti
/// a zmenšení umí aplikace (stejná příprava jako u výběru příloh).
enum ShareCollector {

    static func collect(_ inputItems: [NSExtensionItem], into folder: URL) async -> CollectedShare {
        var texts: [String] = []
        var items: [ShareManifestItem] = []
        var index = 0

        for provider in inputItems.flatMap({ $0.attachments ?? [] }) {
            index += 1
            if let item = await file(from: provider, index: index, into: folder) {
                items.append(item)
            } else if let text = await text(from: provider) {
                texts.append(text)
            }
        }

        let text = texts.joined(separator: "\n")
        return CollectedShare(text: text.isEmpty ? nil : text, items: items)
    }

    private static func file(from provider: NSItemProvider, index: Int, into folder: URL) async -> ShareManifestItem? {
        if provider.hasItemConformingToTypeIdentifier(UTType.movie.identifier),
           let name = await copyRepresentation(provider, type: .movie, index: index, into: folder) {
            return ShareManifestItem(file: name, kind: "video")
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
            if let name = await copyRepresentation(provider, type: .image, index: index, into: folder) {
                return ShareManifestItem(file: name, kind: "image")
            }
            // Snímek obrazovky z editoru a některé aplikace posílají jen UIImage, ne soubor.
            if let image = try? await provider.loadItem(forTypeIdentifier: UTType.image.identifier) as? UIImage,
               let data = image.jpegData(compressionQuality: 0.9) {
                let name = "\(index)-image.jpg"
                if (try? data.write(to: folder.appendingPathComponent(name))) != nil {
                    return ShareManifestItem(file: name, kind: "image")
                }
            }
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier),
           let url = try? await provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier) as? URL {
            let name = "\(index)-\(url.lastPathComponent)"
            guard (try? FileManager.default.copyItem(at: url, to: folder.appendingPathComponent(name))) != nil else { return nil }
            return ShareManifestItem(file: name, kind: kind(of: url))
        }
        // Soubor bez známého typu (PDF z jiné aplikace…) — cokoli, co je data a ne odkaz nebo text.
        if !provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
           !provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
           provider.hasItemConformingToTypeIdentifier(UTType.data.identifier),
           let name = await copyRepresentation(provider, type: .data, index: index, into: folder) {
            return ShareManifestItem(file: name, kind: kind(of: folder.appendingPathComponent(name)))
        }
        return nil
    }

    private static func text(from provider: NSItemProvider) async -> String? {
        if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
           let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
            return url.absoluteString
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
           let text = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String {
            return text
        }
        return nil
    }

    /// Kopie souborové reprezentace; jméno souboru s pořadím, aby se dvě `IMG_0001.jpg` nepřepsaly.
    private static func copyRepresentation(_ provider: NSItemProvider, type: UTType, index: Int, into folder: URL) async -> String? {
        await withCheckedContinuation { continuation in
            _ = provider.loadFileRepresentation(forTypeIdentifier: type.identifier) { url, _ in
                guard let url else {
                    continuation.resume(returning: nil)
                    return
                }
                let name = "\(index)-\(url.lastPathComponent)"
                let copied = (try? FileManager.default.copyItem(at: url, to: folder.appendingPathComponent(name))) != nil
                continuation.resume(returning: copied ? name : nil)
            }
        }
    }

    private static func kind(of url: URL) -> String {
        guard let type = UTType(filenameExtension: url.pathExtension) else { return "file" }
        if type.conforms(to: .movie) { return "video" }
        if type.conforms(to: .image) { return "image" }
        return "file"
    }
}
