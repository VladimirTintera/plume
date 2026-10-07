import Intents
import SwiftUI

/// Stav listu sdílení: snímek chatů od aplikace, hledání a odesílání do vybraného chatu.
@MainActor
final class ShareModel: ObservableObject {
    @Published var query = ""
    @Published private(set) var chats: [ShareSnapshotChat]
    @Published private(set) var busy = false
    @Published private(set) var failed = false

    /// Chat z návrhu v systémové nabídce sdílení (darovaný `INSendMessageIntent`): výběr se
    /// přeskočí a obsah jde rovnou do něj.
    let suggested: ShareSnapshotChat?

    private let context: NSExtensionContext?
    private let openApp: (URL) -> Void

    init(context: NSExtensionContext?, openApp: @escaping (URL) -> Void) {
        self.context = context
        self.openApp = openApp
        let chats = ShareStore.loadSnapshot()?.chats ?? []
        self.chats = chats
        self.suggested = Self.suggestedChat(context, in: chats)
    }

    /// Spustí se s listem: z návrhu rovnou odešle, jinak čeká na výběr.
    func start() {
        if let suggested { send(to: suggested) }
    }

    /// Id chatu z návrhu. Chat, který snímek nezná (třeba už vypadl z padesáti nejnovějších),
    /// se pošle podle id — aplikace ho zná.
    private static func suggestedChat(_ context: NSExtensionContext?, in chats: [ShareSnapshotChat]) -> ShareSnapshotChat? {
        guard let intent = context?.intent as? INSendMessageIntent,
              let raw = intent.conversationIdentifier,
              let id = Int64(raw) else { return nil }
        return chats.first { $0.id == id }
            ?? ShareSnapshotChat(id: id, title: intent.speakableGroupName?.spokenPhrase ?? "", avatar: nil, saved: nil)
    }

    var filtered: [ShareSnapshotChat] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return chats }
        return chats.filter { $0.title.localizedCaseInsensitiveContains(trimmed) }
    }

    func cancel() {
        context?.cancelRequest(withError: NSError(domain: "eu.tintera.plume.share", code: NSUserCancelledError))
    }

    /// Zkopíruje obsah do inboxu, zapíše manifest a otevře Plume, které ho pošle do [chat].
    func send(to chat: ShareSnapshotChat) {
        guard !busy else { return }
        busy = true
        let inputItems = context?.inputItems.compactMap { $0 as? NSExtensionItem } ?? []
        Task {
            do {
                let folder = try ShareStore.newInboxFolder()
                let collected = await ShareCollector.collect(inputItems, into: folder)
                try ShareStore.writeManifest(
                    ShareManifest(chatId: chat.id, text: collected.text, items: collected.items),
                    to: folder
                )
                // Odeslat umí jen aplikace (TDLib v rozšíření neběží). Když se ji otevřít
                // nepodaří, sdílení počká v inboxu na její nejbližší spuštění.
                if let url = URL(string: "eu.tintera.plume://share") { openApp(url) }
                context?.completeRequest(returningItems: nil)
            } catch {
                busy = false
                failed = true
            }
        }
    }
}

struct ShareView: View {
    @ObservedObject var model: ShareModel

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(Text("share.title"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("share.cancel") { model.cancel() }
                    }
                }
        }
        .overlay {
            if model.busy {
                ProgressView().controlSize(.large)
            }
        }
        .alert("share.failed", isPresented: .constant(model.failed)) {
            Button("share.cancel") { model.cancel() }
        }
        .task { model.start() }
    }

    @ViewBuilder
    private var content: some View {
        if model.suggested != nil {
            // Chat je vybraný návrhem; seznam by jen problikl, než se list zavře.
            Color.clear
        } else if model.chats.isEmpty {
            // Snímek chatů zapisuje aplikace — bez přihlášení a prvního spuštění žádný není.
            ContentUnavailableView("share.empty.title", systemImage: "bubble.left.and.bubble.right", description: Text("share.empty.message"))
        } else {
            List(model.filtered) { chat in
                Button { model.send(to: chat) } label: { ChatRow(chat: chat) }
                    .buttonStyle(.plain)
                    .disabled(model.busy)
            }
            .listStyle(.plain)
            .searchable(text: $model.query, placement: .navigationBarDrawer(displayMode: .always), prompt: Text("share.search"))
        }
    }
}

private struct ChatRow: View {
    let chat: ShareSnapshotChat

    var body: some View {
        HStack(spacing: 12) {
            avatar
                .frame(width: 40, height: 40)
                .clipShape(Circle())
            Text(chat.title)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var avatar: some View {
        if chat.saved == true {
            // Uložené zprávy jako v aplikaci: záložka na plné barvě.
            ZStack {
                Circle().fill(Color.accentColor)
                Image(systemName: "bookmark.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
            }
        } else if let name = chat.avatar,
           let url = ShareStore.avatarURL(name),
           let image = UIImage(contentsOfFile: url.path) {
            Image(uiImage: image).resizable().scaledToFill()
        } else {
            // Iniciála jako v aplikaci; barvy ze systémových, list sdílení nemá téma aplikace.
            ZStack {
                Circle().fill(Color.accentColor.opacity(0.18))
                Text(String(chat.title.prefix(1)).uppercased())
                    .font(.headline)
                    .foregroundStyle(Color.accentColor)
            }
        }
    }
}
