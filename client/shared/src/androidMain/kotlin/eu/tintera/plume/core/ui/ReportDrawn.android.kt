package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.activity.compose.ReportDrawnWhen as ActivityReportDrawnWhen

@Composable
actual fun ReportDrawnWhen(ready: () -> Boolean) = ActivityReportDrawnWhen(ready)
