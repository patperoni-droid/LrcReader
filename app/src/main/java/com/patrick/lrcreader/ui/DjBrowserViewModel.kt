package com.patrick.lrcreader.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DjBrowserViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val KEY_ROOT = "dj_root_uri"
        const val KEY_CURRENT = "dj_current_uri"
        const val KEY_STACK = "dj_stack_uris" // List<String>
    }

    private var rootUriString by mutableStateOf(savedStateHandle.get<String?>(KEY_ROOT))
    private var currentUriString by mutableStateOf(savedStateHandle.get<String?>(KEY_CURRENT))
    private var stackStrings by mutableStateOf(savedStateHandle.get<List<String>>(KEY_STACK) ?: emptyList())

    val rootFolderUri: Uri?
        get() = rootUriString?.let { Uri.parse(it) }

    val currentFolderUri: Uri?
        get() = currentUriString?.let { Uri.parse(it) }

    val folderStack: List<Uri>
        get() = stackStrings.map { Uri.parse(it) }

    fun setRoot(uri: Uri?) {
        rootUriString = uri?.toString()
        savedStateHandle[KEY_ROOT] = rootUriString
    }

    fun setCurrent(uri: Uri?) {
        currentUriString = uri?.toString()
        savedStateHandle[KEY_CURRENT] = currentUriString
    }

    fun clearStack() {
        stackStrings = emptyList()
        savedStateHandle[KEY_STACK] = stackStrings
    }

    fun pushCurrent(uri: Uri) {
        stackStrings = stackStrings + uri.toString()
        savedStateHandle[KEY_STACK] = stackStrings
    }

    fun popToParentOrRoot() {
        val newStack = stackStrings.dropLast(1)
        stackStrings = newStack
        savedStateHandle[KEY_STACK] = stackStrings

        val parent = newStack.lastOrNull()?.let { Uri.parse(it) } ?: rootFolderUri
        setCurrent(parent)
    }
}