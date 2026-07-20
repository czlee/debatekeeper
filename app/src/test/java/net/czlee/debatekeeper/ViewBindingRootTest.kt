/*
 * Copyright (C) 2026 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 */

package net.czlee.debatekeeper

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import net.czlee.debatekeeper.databinding.FragmentDownloadFormatsBinding
import net.czlee.debatekeeper.databinding.FragmentFormatChooserBinding
import net.czlee.debatekeeper.databinding.ViewFormatDownloadBinding
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for a subtle ViewBinding/Kotlin interaction: if a layout contains a view
 * with `android:id="@+id/root"`, the generated binding class has both a `root` *field* (that
 * view) and a `getRoot()` *method* (the layout root), and Kotlin resolves `binding.root` to
 * the field — which shadows the method. If that view is a child, fragments returning
 * `binding.root` from `onCreateView` return a view that already has a parent, crashing on
 * launch with "The specified child already has a parent".
 *
 * These tests pin, in Kotlin (so `.root` resolves the same way as in production code), that
 * each binding's `root` is the actual layout root, with no parent.
 */
@RunWith(RobolectricTestRunner::class)
class ViewBindingRootTest {

    private val inflater: LayoutInflater = LayoutInflater.from(ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(), R.style.Debatekeeper_Dark))

    @Test
    fun formatChooserBindingRootIsLayoutRoot() {
        val binding = FragmentFormatChooserBinding.inflate(inflater)
        assertSame((binding as androidx.viewbinding.ViewBinding).root, binding.root)
        assertNull(binding.root.parent)
    }

    @Test
    fun downloadFormatsBindingRootIsLayoutRoot() {
        val binding = FragmentDownloadFormatsBinding.inflate(inflater)
        assertSame((binding as androidx.viewbinding.ViewBinding).root, binding.root)
        assertNull(binding.root.parent)
    }

    @Test
    fun viewFormatDownloadBindingRootIsLayoutRoot() {
        val binding = ViewFormatDownloadBinding.inflate(inflater)
        assertSame((binding as androidx.viewbinding.ViewBinding).root, binding.root)
        assertNull(binding.root.parent)
    }
}
