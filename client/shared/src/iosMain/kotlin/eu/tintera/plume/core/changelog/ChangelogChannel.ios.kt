package eu.tintera.plume.core.changelog

// iOS se zatím nevydává (tagy `ios-…` nejsou), takže ukazuje vydání Androidu jako dřív.
// S prvním iOS releasem sem patří „ios" a kanál do CHANNELS v release_notes.py.
internal actual val changelogChannel: String = "android"
