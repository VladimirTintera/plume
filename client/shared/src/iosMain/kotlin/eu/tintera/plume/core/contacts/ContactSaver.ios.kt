package eu.tintera.plume.core.contacts

import platform.Contacts.CNContactStore
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNLabelPhoneNumberMobile
import platform.Contacts.CNLabelWork
import platform.Contacts.CNMutableContact
import platform.Contacts.CNPhoneNumber
import platform.ContactsUI.CNContactViewController
import platform.ContactsUI.CNContactViewControllerDelegateProtocol
import platform.Contacts.CNContact
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * iOS: `CNContactViewController(forNewContact:)` v navigačním kontroleru (bez něj editor nemá
 * lištu s Hotovo a Zrušit). Ukládá editor do úložiště kontaktů; přístup k nim chce popis
 * `NSContactsUsageDescription` v Info.plist.
 */
actual class ContactSaver {

    /** Delegát editoru je `weak`, drží ho tahle třída, dokud je editor otevřený. */
    private var delegate: Closer? = null

    actual fun save(contact: NewContact): Boolean {
        val top = topViewController() ?: return false
        // Vlastnosti CNMutableContact jsou v Kotlin/Native jen ke čtení (dědí je z CNContact),
        // zapisuje se settery.
        val draft = CNMutableContact().apply {
            contact.name?.let { name ->
                setGivenName(name.substringBefore(' '))
                setFamilyName(name.substringAfter(' ', missingDelimiterValue = ""))
            }
            contact.phone?.let {
                setPhoneNumbers(listOf(CNLabeledValue.labeledValueWithLabel(CNLabelPhoneNumberMobile, CNPhoneNumber.phoneNumberWithStringValue(it))))
            }
            contact.email?.let {
                setEmailAddresses(listOf(CNLabeledValue.labeledValueWithLabel(CNLabelWork, NSString.create(string = it))))
            }
        }
        val closer = Closer { delegate = null }
        delegate = closer
        val editor = CNContactViewController.viewControllerForNewContact(draft).apply {
            contactStore = CNContactStore()
            this.delegate = closer
        }
        top.presentViewController(UINavigationController(rootViewController = editor), animated = true, completion = null)
        return true
    }

    private class Closer(private val onClose: () -> Unit) : NSObject(), CNContactViewControllerDelegateProtocol {
        override fun contactViewController(viewController: CNContactViewController, didCompleteWithContact: CNContact?) {
            viewController.dismissViewControllerAnimated(true, completion = null)
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
