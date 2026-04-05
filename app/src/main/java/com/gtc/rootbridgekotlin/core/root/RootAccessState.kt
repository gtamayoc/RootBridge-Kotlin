package com.gtc.rootbridgekotlin.core.root

sealed class RootAccessState {
    data object Idle : RootAccessState()
    data object Checking : RootAccessState()
    data object Authorized : RootAccessState()
    data class Denied(val reason: String) : RootAccessState()
    data object MissingBinary : RootAccessState()
}
