package eu.tintera.plume.core.calendar

import kotlinx.datetime.TimeZone
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKitUI.EKEventEditViewAction
import platform.EventKitUI.EKEventEditViewController
import platform.EventKitUI.EKEventEditViewDelegateProtocol
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * iOS: `EKEventEditViewController` s předvyplněnou událostí. Od iOS 17 běží mimo proces
 * aplikace a přístup ke kalendáři nepotřebuje — uloží ji uživatel sám, a aplikace se nic
 * nedozví. Projekt cílí na iOS 18.2, takže starší cesta s žádostí o přístup tu není.
 */
actual class CalendarEvents {

    private val store = EKEventStore()

    /** Delegát editoru je `weak`, drží ho tahle třída, dokud je editor otevřený. */
    private var delegate: Closer? = null

    actual fun add(event: CalendarEvent): Boolean {
        val top = topViewController() ?: return false
        val (begin, end) = event.span(TimeZone.currentSystemDefault())
        val draft = EKEvent.eventWithEventStore(store).apply {
            title = event.title
            location = event.place
            notes = event.description
            allDay = event.allDay
            startDate = NSDate.dateWithTimeIntervalSince1970(begin.epochSeconds.toDouble())
            endDate = NSDate.dateWithTimeIntervalSince1970(end.epochSeconds.toDouble())
        }
        val closer = Closer { delegate = null }
        delegate = closer
        val editor = EKEventEditViewController().apply {
            eventStore = store
            this.event = draft
            editViewDelegate = closer
        }
        top.presentViewController(editor, animated = true, completion = null)
        return true
    }

    private class Closer(private val onClose: () -> Unit) : NSObject(), EKEventEditViewDelegateProtocol {
        override fun eventEditViewController(controller: EKEventEditViewController, didCompleteWithAction: EKEventEditViewAction) {
            controller.dismissViewControllerAnimated(true, completion = null)
            onClose()
        }
    }

    /** Nejvýš položený kontroler — Compose obrazovka sama může něco prezentovat (sheet). */
    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: break
        return top
    }
}
