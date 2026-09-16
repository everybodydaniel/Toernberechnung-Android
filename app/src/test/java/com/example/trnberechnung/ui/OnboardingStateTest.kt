package com.example.trnberechnung.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun `warnings page is inserted before the final disclaimer page`() {
        assertEquals(4, ONBOARDING_PAGES)
        assertEquals(3, OnboardingState.LAST_PAGE)
        assertFalse(OnboardingState(page = 2, disclaimerAccepted = true).canFinish)
    }

    @Test
    fun `finish is disabled until the disclaimer is accepted on the last page`() {
        assertFalse(OnboardingState(page = OnboardingState.LAST_PAGE).canFinish)
        assertTrue(OnboardingState(page = OnboardingState.LAST_PAGE, disclaimerAccepted = true).canFinish)
    }

    @Test
    fun `disclaimer alone cannot finish an earlier page`() {
        assertFalse(OnboardingState(page = 1, disclaimerAccepted = true).canFinish)
    }
}
