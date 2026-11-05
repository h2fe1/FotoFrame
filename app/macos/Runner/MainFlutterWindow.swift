import Cocoa
import FlutterMacOS

/// 承载 Flutter 视图的容器：注册为文件拖拽目标，鼠标事件仍透传给 Flutter。
final class DropContainerView: NSView {
  var onFilesDropped: (([String]) -> Void)?

  override init(frame frameRect: NSRect) {
    super.init(frame: frameRect)
    registerForDraggedTypes([.fileURL])
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    registerForDraggedTypes([.fileURL])
  }

  private func fileURLs(from sender: NSDraggingInfo) -> [URL] {
    let options: [NSPasteboard.ReadingOptionKey: Any] = [
      .urlReadingFileURLsOnly: true
    ]
    let urls = sender.draggingPasteboard.readObjects(
      forClasses: [NSURL.self], options: options) as? [URL] ?? []
    let exts: Set<String> = ["jpg", "jpeg", "png", "heic", "tif", "tiff", "dng"]
    return urls.filter { exts.contains($0.pathExtension.lowercased()) }
  }

  override func draggingEntered(_ sender: NSDraggingInfo) -> NSDragOperation {
    return fileURLs(from: sender).isEmpty ? [] : .copy
  }

  override func draggingUpdated(_ sender: NSDraggingInfo) -> NSDragOperation {
    return fileURLs(from: sender).isEmpty ? [] : .copy
  }

  override func performDragOperation(_ sender: NSDraggingInfo) -> Bool {
    let urls = fileURLs(from: sender)
    guard !urls.isEmpty else { return false }
    onFilesDropped?(urls.map { $0.path })
    return true
  }
}

class MainFlutterWindow: NSWindow {
  /// 强引用，避免 FlutterViewController 被释放。
  private var flutterViewController: FlutterViewController?
  private var methodChannel: FlutterMethodChannel?
  private var dropContainer: DropContainerView?

  override func awakeFromNib() {
    let flutterViewController = FlutterViewController()
    let windowFrame = self.frame
    self.flutterViewController = flutterViewController

    // 用容器包住 Flutter 视图，容器负责接收文件拖拽；
    // 不对 window 设置 contentViewController，避免 Flutter 视图被摘挂。
    let container = DropContainerView(frame: windowFrame)
    container.autoresizingMask = [.width, .height]
    self.dropContainer = container

    flutterViewController.view.frame = container.bounds
    flutterViewController.view.autoresizingMask = [.width, .height]
    container.addSubview(flutterViewController.view)

    self.contentView = container
    self.setFrame(windowFrame, display: true)

    // 内容铺满窗口：隐藏标题、透明标题栏，红绿灯浮在自绘工具栏左上角
    self.titleVisibility = .hidden
    self.titlebarAppearsTransparent = true
    self.styleMask.insert(.fullSizeContentView)
    self.isMovableByWindowBackground = false
    self.backgroundColor = NSColor.windowBackgroundColor
    // 不参与系统窗口状态恢复，尺寸由下面的初始窗口逻辑决定
    self.isRestorable = false
    self.setFrameAutosaveName("")
    self.setContentSize(NSSize(width: 1440, height: 900))
    self.contentMinSize = NSSize(width: 1120, height: 700)
    RegisterGeneratedPlugins(registry: flutterViewController)

    let channel = FlutterMethodChannel(
      name: "fotoframe/platform",
      binaryMessenger: flutterViewController.engine.binaryMessenger)
    self.methodChannel = channel

    channel.setMethodCallHandler { call, result in
      switch call.method {
      case "copyImage":
        guard let args = call.arguments as? [String: Any],
              let data = args["png"] as? FlutterStandardTypedData else {
          result(FlutterError(code: "bad_args", message: "缺少 png 数据", details: nil))
          return
        }
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        result(pasteboard.setData(data.data, forType: .png))
      default:
        result(FlutterMethodNotImplemented)
      }
    }

    container.onFilesDropped = { paths in
      channel.invokeMethod("filesDropped", arguments: paths)
    }

    super.awakeFromNib()

    // 初始窗口：等首帧布局完成后再定尺寸，避免被 Flutter 视图的初始尺寸覆盖
    DispatchQueue.main.async { [weak self] in
      guard let self = self, let screen = self.screen ?? NSScreen.main else { return }
      let visible = screen.visibleFrame
      let width = min(1440, visible.width - 40)
      let height = min(900, visible.height - 40)
      let frame = NSRect(
        x: visible.midX - width / 2,
        y: visible.midY - height / 2,
        width: width,
        height: height)
      self.setFrame(frame, display: true)
      // 把红绿灯垂直居中到 54px 工具栏里
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in
        self?.centerTrafficLights()
      }
    }
  }

  /// 红绿灯默认停在 28px 标题栏里；这里把它们下移到 54px 工具栏的中线。
  private func centerTrafficLights() {
    guard let close = standardWindowButton(.closeButton),
          let mini = standardWindowButton(.miniaturizeButton),
          let zoom = standardWindowButton(.zoomButton),
          let sv = close.superview else { return }
    let targetCenterY = frame.height - 27
    let center = sv.convert(
      NSPoint(x: close.frame.midX, y: close.frame.midY), to: nil)
    let delta = targetCenterY - center.y
    for b in [close, mini, zoom] {
      b.setFrameOrigin(
        NSPoint(x: b.frame.origin.x, y: b.frame.origin.y + delta))
    }
  }
}
