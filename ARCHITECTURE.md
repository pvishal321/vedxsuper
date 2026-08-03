# Architecture Documentation

## Technology Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Architecture:** MVVM (Model-View-ViewModel)
- **State Management:** StateFlow, Coroutines
- **Networking:** Retrofit, OkHttp, WebSocket
- **Database:** Room
- **Navigation:** Navigation Compose
- **Minimum SDK:** API 29

## Project Structure
Package name: `com.vedx.vedxsuper`

The following modules/packages must be maintained with single responsibility:
- `ui`: UI components and screens
- `broker`: Broker specific logic (Angel One)
- `websocket`: Real-time data handling
- `strategy`: Trading logic (SuperTrend)
- `trade`: Virtual trading and execution logic
- `database`: Local persistence
- `repository`: Data abstraction layer
- `model`: Data classes
- `notification`: User alerts
- `utils`: Helper classes

## Component Specific Rules

### WebSocket
- Receive live market data.
- Reconnect automatically.
- Keep latency as low as possible.
- Avoid unnecessary processing.

### Virtual Trading
- Paper trading only.
- Track: Entry, Exit, Stop Loss, Target, Quantity, Brokerage, Profit/Loss.
- Store every trade locally using Room Database.
