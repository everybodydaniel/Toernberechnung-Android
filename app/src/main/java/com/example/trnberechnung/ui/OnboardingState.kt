package com.example.trnberechnung.ui

internal const val ONBOARDING_PAGES = 4

/** Pure state used by the last onboarding page and covered by unit tests. */
data class OnboardingState(
    val page: Int = 0,
    val disclaimerAccepted: Boolean = false
) {
    val canFinish: Boolean get() = page == LAST_PAGE && disclaimerAccepted

    companion object {
        const val LAST_PAGE = ONBOARDING_PAGES - 1
    }
}
