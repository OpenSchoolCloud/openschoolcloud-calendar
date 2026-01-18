// OpenSchoolCloudCalendarApp.swift
// OpenSchoolCloud Calendar
//
// Created by OpenSchoolCloud Team
// Copyright © 2025 OpenSchoolCloud / Aldewereld Consultancy
// Licensed under Apache License 2.0

import SwiftUI

@main
struct OpenSchoolCloudCalendarApp: App {
    
    @StateObject private var appState = AppState()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
        }
    }
}

// MARK: - App State

class AppState: ObservableObject {
    @Published var isLoggedIn: Bool = false
    @Published var currentAccount: Account?
    
    init() {
        // Check for existing account
        checkExistingAccount()
    }
    
    private func checkExistingAccount() {
        // TODO: Load from Keychain
    }
}

// MARK: - Models (placeholder)

struct Account: Identifiable, Codable {
    let id: String
    let serverUrl: String
    let username: String
    var displayName: String?
}
