package eu.tintera.plume.core.ai

/**
 * Umí platforma popisovat fotky na pozadí (úloha na nabíječce)? Android ano (WorkManager,
 * `PhotoDescriptionWorker`); iOS by chtěl `BGProcessingTask` a model, který obraz bere, desktop
 * model nemá. Kde ne, volba v Nastavení není.
 */
expect val backgroundPhotoDescriptionsSupported: Boolean
