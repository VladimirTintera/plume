package eu.tintera.plume

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

/**
 * Rodič Compose controlleru, který řídí ikony stavové lišty podle ZVOLENÉHO motivu.
 *
 * - **Styl lišty, ne `overrideUserInterfaceStyle`.** Přepsaný vzhled okna by Compose četl jako
 *   systémový (`isSystemInDarkTheme` bere `traitCollection` controlleru): motiv „podle systému"
 *   by se zacyklil na sebe a tmavý prohlížeč médií (`DarkSystemBars`) by přebarvil aplikaci pod
 *   sebou.
 * - **Rodič, protože Compose controller je interní.** Jeho `ComposeUIViewControllerDelegate`
 *   s `preferredStatusBarStyle` je zastaralý a Compose sám radí obalit ho rodičem.
 * - **Plocha pod lištou styl nepotřebuje** — obsah je přes celou obrazovku
 *   (`ignoresSafeArea` v ContentView.swift), lišta je průhledná a barvu motivu má sama.
 */
@OptIn(ExperimentalForeignApi::class)
internal class StatusBarHostViewController(
    private val content: UIViewController,
) : UIViewController(nibName = null, bundle = null) {

    var isDark: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            setNeedsStatusBarAppearanceUpdate()
        }

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(content)
        content.view.setFrame(view.bounds)
        content.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(content.view)
        content.didMoveToParentViewController(this)
    }

    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        if (isDark) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent
}

/** Kudy `SystemBarsAppearance` dosáhne na hostitele lišty; controller je jeden na proces. */
internal object StatusBarHost {
    var controller: StatusBarHostViewController? = null
}
