# Huafetcher for Android

> **Note**: This app was converted with AI from the original python/kivy app at [https://codeberg.org/vanous/huafetcher](https://codeberg.org/vanous/huafetcher).

Huafetcher is a modern Android application written in Kotlin and Jetpack Compose for Amazfit, Xiaomi, and Huami wearable devices. It fetches device authentication keys (for companion apps such as Gadgetbridge) and downloads/packages aGPS satellite data to speed up GPS fixes on your smartwatch or fitness band.

---

## 🌟 Key Features

- **Device Auth Key Retrieval**: Obtain MAC addresses and unique device `auth_key` values for pairing Amazfit and Xiaomi wearables with third-party tools like Gadgetbridge.
- **aGPS Ephemeris & Almanac Downloader**: Download satellite data packs from Huami servers:
  - `cep_1week.zip` & `cep_7days.zip` (GPS CEP)
  - `lle_1week.zip` (LLE / Long-term Orbit)
  - `cep_pak.bin`
  - `epo.zip` (EPO)
  - `lto.zip` (LTO)
- **aGPS Binary Packager (`aGPS_UIHH.bin`)**: Automatically unpacks, verifies CRC32 checksums, and compiles component almanac files (`gps_alm.bin`, `gln_alm.bin`, `lle_*.lle`) into standard `aGPS_UIHH.bin` and `gps_uihh.bin` binaries with custom `UIHH` headers.
- **Dual Authentication Modes**:
  - **Amazfit Account**: Direct email and password login.
  - **Xiaomi / Mi Account**: Built-in WebView OAuth authorization flow.
- **Flexible Data Storage**:
  - Store generated files in External App Storage, Public Downloads, or Internal App Storage.
  - Custom folder name configuration.
  - One-click file cleaning and folder management.
- **First-Start Wizard & Quick Setup**: Guided onboarding workflow for first-time users to set up login credentials, preferred export files, and storage locations.

---

## 🛠️ Architecture & Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel) with StateFlow
- **Networking**: OkHttp3
- **Data Persistence**: Android SharedPreferences (`PreferencesManager`)
- **Binary & ZIP Processing**: `java.util.zip` (ZipInputStream, CRC32), Little-Endian binary packaging

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or later
- JDK 17+
- Android SDK (API 26+)

### Build & Installation
1. Clone the repository:
   ```bash
   git clone <repository-url>
   ```
2. Open the project in Android Studio.
3. Build the debug APK using Gradle:
   ```bash
   gradle assembleDebug
   ```
4. Install on your Android device or emulator:
   ```bash
   gradle installDebug
   ```

---

## 📱 Usage

1. **Initial Setup**: On first run, complete the **First-Start Setup Wizard** to configure your login method (Amazfit or Xiaomi Account) and select storage preferences.
2. **Fetch Auth Keys**: On the main dashboard, tap **Fetch Auth Keys** to retrieve your connected wearable device's MAC address and key.
3. **Fetch aGPS Data**: Tap **Fetch aGPS Data** to download the latest satellite data packs.
4. **Build aGPS_UIHH.bin**: Tap **Pack UIHH File** to synthesize component almanac packs into a ready-to-flash `aGPS_UIHH.bin`.
5. **Export & Share**: Share or export generated files directly to Gadgetbridge or your filesystem.

---

## 📄 License & Acknowledgments

Converted with AI from the original Python/Kivy **Huafetcher** project by **vanous**:
- Original Repository: [https://codeberg.org/vanous/huafetcher](https://codeberg.org/vanous/huafetcher)
