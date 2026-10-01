<img src="assets/icon.svg" width="64" height="64" alt="">

# hungii

Created in [T3 Code](https://t3.codes).

## Android prototype

The current prototype is a native Kotlin / Jetpack Compose Android app with charcoal surfaces, electric lime, an animated voice orb, swipe-to-shortlist meal cards, and a face-down lucky draw.

Build and run instructions: [android-prototype/README.md](android-prototype/README.md). Requires Android 8.0 or newer. The downloadable build is generated at `artifacts/hungii-android-prototype.apk`.

Version 0.3 removes the sample catalogue and prepares a Swiggy Food MCP integration backed by Supabase Auth/Postgres in Mumbai. The APK starts disconnected until external service setup is completed. Room retains user-entered tracking and consented local favorites. Missing nutrition stays unknown, and checkout currently opens Swiggy rather than modifying a cart or placing an order.

Follow [service setup](docs/swiggy-setup.md). The [stack decision](docs/adr/0001-native-android-and-mumbai-backend.md) and [verified MCP contract](docs/swiggy-integration-contract.md) record the implementation boundaries. Supabase and Swiggy access have not been provisioned in this workspace.

## Earlier web exploration

```sh
python3 prototype/serve.py
```

Open [Hungii](http://localhost:5173/prototype/?variant=a) to review the earlier Companion, Pocket, and Daybook layouts. The Android prototype supersedes these visual directions.

Swipe to shortlist three meals, shuffle them face down, and pick a card. The orb supports scripted text updates and optional browser voice. Meals, nutrition, prices, coupons, and checkout are sample data; nothing is ordered.

See [prototype notes](prototype/README.md) and the [product specification](docs/meal-planner-product.md).
