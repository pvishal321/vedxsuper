package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.trade.VirtualTradeManager
import kotlinx.coroutines.flow.MutableStateFlow

class DashboardViewModel(
    private val tokenManager: SecureTokenManager,
    private val virtualTradeManager: VirtualTradeManager
) : ViewModel() {
    // Add dashboard specific logic here if needed
}
