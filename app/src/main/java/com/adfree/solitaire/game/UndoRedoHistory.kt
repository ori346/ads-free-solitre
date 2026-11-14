package com.adfree.solitaire.game

import com.adfree.solitaire.config.DefaultGameConfig

/**
 * Bounded generic undo/redo state history stack.
 *
 * @param T Snapshot type stored in the history.
 * @property maxSize Maximum number of undo snapshots retained.
 */
class UndoRedoHistory<T>(
    private val maxSize: Int = DefaultGameConfig.rules.maxUndoHistory,
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    /** True when at least one previous state can be restored. */
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** True when at least one undone state can be reapplied. */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Records [current] onto the undo stack, evicting the oldest entry if over [maxSize], and clears redo. */
    fun record(current: T) {
        if (maxSize <= 0) {
            clear()
            return
        }
        pushBoundedUndo(current)
        redoStack.clear()
    }

    /** Pops the most recent undo snapshot and pushes [current] onto the redo stack, or returns `null`. */
    fun undo(current: T): T? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return prev
    }

    /** Pops the most recent redo snapshot and pushes [current] onto the undo stack, or returns `null`. */
    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        if (maxSize > 0) pushBoundedUndo(current)
        return next
    }

    private fun pushBoundedUndo(item: T) {
        undoStack.addLast(item)
        while (undoStack.size > maxSize) {
            undoStack.removeFirst()
        }
    }

    /** Replaces the undo stack with [items] (trimmed to [maxSize]) and clears redo. */
    fun loadHistory(items: List<T>) {
        undoStack.clear()
        redoStack.clear()
        if (maxSize <= 0) return
        val bounded = if (items.size > maxSize) items.takeLast(maxSize) else items
        undoStack.addAll(bounded)
    }

    /** Returns the undo stack ordered from oldest to most recent. */
    fun toList(): List<T> = undoStack.toList()

    /** Returns the oldest recorded undo snapshot, or `null` if empty. */
    fun firstOrNull(): T? = undoStack.firstOrNull()

    /** True when no undo snapshots are recorded. */
    fun isEmpty(): Boolean = undoStack.isEmpty()

    /** Clears both undo and redo stacks. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
