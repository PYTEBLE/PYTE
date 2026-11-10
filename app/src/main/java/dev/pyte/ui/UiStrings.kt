package dev.pyte.ui

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Resource-backed messages for ViewModels; never retains an Activity. */
class UiStrings @Inject constructor(@ApplicationContext private val context: Context) {
    fun get(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)
}
