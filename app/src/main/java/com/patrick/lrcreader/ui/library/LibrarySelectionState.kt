package com.patrick.lrcreader.ui.library

internal data class LibrarySelectionState<K>(
    val selectedKeys: Set<K> = emptySet()
) {
    val isActive: Boolean
        get() = selectedKeys.isNotEmpty()

    fun toggle(key: K): LibrarySelectionState<K> = copy(
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    )

    fun selectAll(keys: Collection<K>): LibrarySelectionState<K> = copy(
        selectedKeys = keys.toSet()
    )

    fun retainOnly(keys: Collection<K>): LibrarySelectionState<K> = copy(
        selectedKeys = selectedKeys.intersect(keys.toSet())
    )

    fun clear(): LibrarySelectionState<K> = copy(selectedKeys = emptySet())
}
