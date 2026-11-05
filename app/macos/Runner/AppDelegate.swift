import Cocoa
import FlutterMacOS

@main
class AppDelegate: FlutterAppDelegate {
  private var appearanceObservation: NSKeyValueObservation?
  private var iconSyncInstalled = false

  override func awakeFromNib() {
    super.awakeFromNib()
    installAppearanceIconSync()
  }

  override func applicationDidFinishLaunching(_ notification: Notification) {
    super.applicationDidFinishLaunching(notification)
    installAppearanceIconSync()
  }

  /// 浅色模式用纸白版，深色模式用墨色版。
  /// 只换运行时的 Dock 图标；包里的 AppIcon.icns 仍是浅色版，
  /// 供 Finder 与「未运行时的 Dock」使用。
  private func installAppearanceIconSync() {
    guard !iconSyncInstalled else { return }
    iconSyncInstalled = true
    syncAppearanceIcon()
    appearanceObservation = NSApp.observe(\.effectiveAppearance, options: [.new]) {
      [weak self] _, _ in
      self?.syncAppearanceIcon()
    }
    DistributedNotificationCenter.default.addObserver(
      self,
      selector: #selector(systemThemeChanged),
      name: NSNotification.Name("AppleInterfaceThemeChangedNotification"),
      object: nil)
    // Dock 在启动收尾时会先用包内图标铺一次，等 runloop 转起来再写一遍
    DispatchQueue.main.async { [weak self] in
      self?.syncAppearanceIcon()
    }
  }

  @objc private func systemThemeChanged() {
    DispatchQueue.main.async { [weak self] in
      self?.syncAppearanceIcon()
    }
  }

  private func syncAppearanceIcon() {
    let isDark = NSApp.effectiveAppearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
    let name = isDark ? "IconDark" : "IconLight"
    guard let icon = NSImage(named: name) else { return }
    NSApp.applicationIconImage = icon
  }

  override func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
    return true
  }

  override func applicationSupportsSecureRestorableState(_ app: NSApplication) -> Bool {
    return true
  }
}