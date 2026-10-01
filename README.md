<img src="assets/icon.svg" width="64" height="64" alt="">

# hungii

Created in [T3 Code](https://t3.codes).

## Android prototype

The current prototype is a native Kotlin / Jetpack Compose Android app with charcoal surfaces, electric lime, an animated voice orb, swipe-to-shortlist meal cards, and a face-down lucky draw.

Build and run instructions: [android-prototype/README.md](android-prototype/README.md). Requires Android 8.0 or newer. The downloadable build is generated at `artifacts/hungii-android-prototype.apk`.

Meals, nutrition ranges, prices and offers are fictional sample data. The prototype does not connect to Swiggy or place orders. Check-ins use a local phrase parser; optional speech recognition uses the device's speech service. State lasts for the current app session.

## Earlier web exploration

```sh
python3 prototype/serve.py
```

Open [Hungii](http://localhost:5173/prototype/?variant=a) to review the earlier Companion, Pocket, and Daybook layouts. The Android prototype supersedes these visual directions.

Swipe to shortlist three meals, shuffle them face down, and pick a card. The orb supports scripted text updates and optional browser voice. Meals, nutrition, prices, coupons, and checkout are sample data; nothing is ordered.

See [prototype notes](prototype/README.md) and the [product specification](docs/meal-planner-product.md).
