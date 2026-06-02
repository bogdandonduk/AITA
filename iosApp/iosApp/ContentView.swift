// THIS IS ContentView.swift - place in iosApp/iosApp/ContentView.swift
import ComposeApp
import SwiftUI

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosMainComposeKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
            .ignoresSafeArea(.container)
    }
}
