package dev.saketanand.setwise.ui.templates

/** One-off things [TemplateEditorViewModel] tells the screen to do. */
sealed interface TemplateEditorEvent {
    data object Saved : TemplateEditorEvent

    /** Back without saving (nothing changed, or changes discarded), deleted, or the template is gone. */
    data object Closed : TemplateEditorEvent

    data object SaveFailed : TemplateEditorEvent
}
