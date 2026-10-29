package octo.feature.task

import octo.core.task.TaskId
import octo.core.task.TaskPlace

/** Where tasks sit in the sidebar. The shell owns it; the task screen only asks to move one. */
interface TaskPlaces {
    fun place(
        id: TaskId,
        place: TaskPlace,
    )

    /** Snoozes [id] for [forMs], or with null until its agent's turn ends. */
    fun snooze(
        id: TaskId,
        forMs: Long?,
    )
}
