package com.vedx.vedxsuper.model.auth

enum class TokenState {
    NO_SESSION,
    LOGIN_REQUIRED,
    AUTHENTICATING,
    VALID,
    EXPIRED,
    REFRESHING,
    FAILED
}
