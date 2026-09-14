import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose si insety řeší sám; bez tohohle by SwiftUI ubralo safe area dvakrát.
            .ignoresSafeArea(.all)
            // Aby klávesnice v přihlašovacím formuláři neposouvala celou Compose plochu.
            .ignoresSafeArea(.keyboard)
    }
}
