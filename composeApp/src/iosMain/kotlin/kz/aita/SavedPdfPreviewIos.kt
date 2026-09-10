@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package kz.aita

import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject

private var savedPdfPreview: Pair<UIDocumentInteractionController, SavedPdfPreviewDelegate>? = null
private class SavedPdfPreviewDelegate(private val host: UIViewController) : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
    override fun documentInteractionControllerViewControllerForPreview(controller: UIDocumentInteractionController): UIViewController = host
    override fun documentInteractionControllerDidEndPreview(controller: UIDocumentInteractionController) {
        if (savedPdfPreview?.first == controller) savedPdfPreview = null
    }
}
internal fun openSavedPdfPreviewIos(path: String): ReceiptPlatformActionResult {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return ReceiptPlatformActionResult(false, "The saved PDF was moved or deleted")
    var host = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: return ReceiptPlatformActionResult(false, "No active window can display the PDF")
    while (host.presentedViewController != null) host = host.presentedViewController!!
    val controller = UIDocumentInteractionController.interactionControllerWithURL(NSURL.fileURLWithPath(path))
    val delegate = SavedPdfPreviewDelegate(host)
    controller.delegate = delegate
    savedPdfPreview = controller to delegate // the native delegate is weak
    val opened = controller.presentPreviewAnimated(true)
    if (!opened) savedPdfPreview = null
    return ReceiptPlatformActionResult(opened, if (opened) "Opening saved PDF" else "The PDF preview is unavailable")
}
