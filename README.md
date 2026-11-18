<div align="center">

# 📡 NetMaster Pro

### تطبيق إدارة الشبكة المتقدم · Advanced Network Management App

**Android · Kotlin · Jetpack Compose · Clean Architecture**

[![Build NetMaster Pro APK](https://github.com/YOUR_USERNAME/NetMasterPro/actions/workflows/build-apk.yml/badge.svg)](https://github.com/YOUR_USERNAME/NetMasterPro/actions/workflows/build-apk.yml)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blueviolet)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

```
  ____  _   _      _   __  __           _            ____
 |  _ \| \ | |    | | |  \/  |         | |          |  _ \
 | | | |  \| | ___| |_| \  / | __ _ ___| |_ ___ _ __| |_) |_ __ ___
 | |_| | . ` |/ _ \ __| |\/| |/ _` / __| __/ _ \ '__|  __/| '__/ _ \
 |____/|_|\__|_|\___/\__|_|  |_|\__,_\___|\__\___/_|  |_|  |_|  \___/
```

[📥 تحميل آخر إصدار / Download Latest APK](#-تحميل-التطبيق--download)

</div>

---

## 🌐 نبذة عن التطبيق · About

<table>
<tr>
<td width="50%">

**عربي**

NetMaster Pro هو تطبيق أندرويد متقدم لإدارة الشبكات المنزلية. يتيح لك مراقبة جميع الأجهزة المتصلة، قياس سرعة الإنترنت، كشف المتطفلين، وإدارة جهاز الراوتر مباشرة من هاتفك — مع دعم خاص لجهاز **Greenpacket D2-220G1** (نموذج VDSL2/FTTH الشائع من STC وعدد من مزودي الإنترنت الخليجيين).

</td>
<td width="50%">

**English**

NetMaster Pro is an advanced Android app for home network management. Monitor all connected devices, measure internet speed, detect intruders, and manage your router directly from your phone — with dedicated support for the **Greenpacket D2-220G1** (a widely-used VDSL2/FTTH router by STC and Gulf ISPs).

</td>
</tr>
</table>

---

## ✨ الميزات · Features

### وحدات التطبيق الثمانية · Eight App Modules

| # | الوحدة (عربي) | Module (English) | الوصف / Description |
|---|---|---|---|
| 1 | 🔍 **ماسح الشبكة** | **Network Scanner** | يكتشف جميع الأجهزة على الشبكة المحلية مع معلومات MAC/IP/الشركة المصنعة · Discovers all LAN devices with MAC/IP/manufacturer info |
| 2 | 📊 **مراقب السرعة** | **Speed Monitor** | قياس سرعة التنزيل والرفع والاتصال بالوقت الفعلي · Real-time download/upload/ping measurement |
| 3 | 🛡️ **كاشف المتطفلين** | **Intruder Detector** | خدمة عمل في الخلفية مع إشعارات فورية عند اتصال جهاز غير معروف · Background service with instant alerts for unknown device connections |
| 4 | 🏠 **واجهة الراوتر** | **Router Manager** | لوحة تحكم ووب مدمجة لإدارة Greenpacket D2-220G1 مباشرة · Embedded web panel for direct Greenpacket D2-220G1 management |
| 5 | 📶 **خريطة الإشارة** | **Signal Mapper** | خريطة حرارية لقوة إشارة WiFi في المنزل · Heat-map of WiFi signal strength throughout your home |
| 6 | 🔒 **الحماية والأمان** | **Security & Auth** | قفل بيومتري، تشفير البيانات المحلية، سجل الأنشطة · Biometric lock, local data encryption, activity logs |
| 7 | 📦 **الصادرات والتقارير** | **Export & Reports** | تصدير بيانات الشبكة إلى PDF/CSV، تقارير أسبوعية · Export network data to PDF/CSV, weekly reports |
| 8 | 🔧 **الأدوات المتقدمة** | **Advanced Tools** | Ping, Traceroute, DNS Lookup, Port Scanner, Wake-on-LAN · نفس العمليات بالعربي |

---

## 🏗️ المعمارية · Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Presentation Layer                     │
│   Jetpack Compose UI · ViewModels (Hilt) · Navigation   │
└─────────────────────┬───────────────────────────────────┘
                      │ observes StateFlow / events
┌─────────────────────▼───────────────────────────────────┐
│                    Domain Layer                          │
│      Use Cases · Repository Interfaces · Models          │
└─────────────────────┬───────────────────────────────────┘
                      │ implements
┌─────────────────────▼───────────────────────────────────┐
│                     Data Layer                           │
│  ┌────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │  Room DB   │  │ Retrofit API │  │ DataStore Prefs │  │
│  │  (local)   │  │  (remote)    │  │   (settings)    │  │
│  └────────────┘  └──────────────┘  └─────────────────┘  │
│  ┌─────────────────────────────────────────────────────┐ │
│  │        WorkManager · Foreground Services             │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

**Pattern:** Clean Architecture + MVVM  
**DI:** Hilt  
**Async:** Kotlin Coroutines + Flow  
**UI:** Jetpack Compose + Material 3  
**Persistence:** Room + DataStore  
**Network:** Retrofit + OkHttp + Kotlinx Serialization

---

## 📡 تكامل Greenpacket D2-220G1 · Greenpacket D2-220G1 Integration

<details>
<summary>اقرأ التفاصيل / Read Details (click to expand)</summary>

### عن الجهاز · About the Device

جهاز **Greenpacket D2-220G1** هو مودم/راوتر VDSL2 مزدوج النطاق (2.4GHz + 5GHz) مشترك في شبكات STC (سعودية)، زين، وأورنج. يتضمن لوحة ويب على `http://192.168.1.1` أو `http://192.168.0.1`.

The **Greenpacket D2-220G1** is a dual-band VDSL2/FTTH modem-router common on STC (Saudi Arabia), Zain, and Orange networks. It exposes a web management panel at `http://192.168.1.1` or `http://192.168.0.1`.

### التكامل في التطبيق · App Integration

| الميزة / Feature | التقنية / Technology |
|---|---|
| جلب قائمة الأجهزة المتصلة / Fetch connected clients | Jsoup HTML parsing of DHCP table |
| مراقبة حالة WAN / WAN status monitoring | Retrofit → router REST endpoints |
| قراءة إحصائيات DSL / DSL stats reading | Jsoup scraping + Regex extraction |
| تسجيل الدخول التلقائي / Auto login | OkHttp cookie jar session management |
| التعامل مع شهادات SSL الذاتية / Self-signed SSL | Custom TrustManager (fingerprint-pinned by user) |

### نقاط النهاية المدعومة / Supported Endpoints

```
GET  /cgi-bin/status.asp          → WAN/DSL status
GET  /cgi-bin/connected_client.asp → DHCP leases / connected devices
POST /cgi-bin/login.cgi            → Authentication
GET  /cgi-bin/reboot.asp           → Reboot router
GET  /cgi-bin/wifi_config.asp      → WiFi SSID/password config
```

</details>

---

## 📥 تحميل التطبيق · Download

### الطريقة الأولى: GitHub Actions (موصى بها)

1. اذهب إلى تبويب **Actions** في هذا المستودع
2. انقر على آخر **Build NetMaster Pro APK** ناجح
3. في قسم **Artifacts** في أسفل الصفحة، حمّل:
   - `NetMaster-Pro-Release` — نسخة الإنتاج (موصى بها)
   - `NetMaster-Pro-Debug` — نسخة التطوير

---

1. Go to the **Actions** tab in this repository
2. Click the latest successful **Build NetMaster Pro APK** run
3. Under **Artifacts** at the bottom of the page, download:
   - `NetMaster-Pro-Release` — production build (recommended)
   - `NetMaster-Pro-Debug` — development build

### الطريقة الثانية: GitHub Releases

عند وسم إصدار (`v1.0.0`)، يُنشأ Release تلقائياً بملفات APK مرفقة.  
When a tag like `v1.0.0` is pushed, a Release is automatically created with APK attachments.

### تمكين التثبيت من مصادر غير معروفة / Enable Unknown Sources

```
الإعدادات → التطبيقات → تثبيت تطبيقات غير معروفة → متصفح الملفات → السماح
Settings → Apps → Install Unknown Apps → Your File Manager → Allow
```

---

## 🛠️ بناء التطبيق محلياً · Local Build

### المتطلبات / Prerequisites

| الأداة / Tool | الإصدار / Version |
|---|---|
| Android Studio | Ladybug 2024.2.1+ |
| JDK | 17 |
| Android SDK | API 35 (compileSdk) |
| Gradle | 8.11.1 (via wrapper) |
| Kotlin | 2.0.21 |

### خطوات البناء / Build Steps

```bash
# 1. استنساخ المستودع / Clone the repository
git clone https://github.com/YOUR_USERNAME/NetMasterPro.git
cd NetMasterPro

# 2. بناء نسخة Debug / Build Debug APK
./gradlew assembleDebug

# موقع الملف / Output location:
# app/build/outputs/apk/debug/app-debug.apk

# 3. بناء نسخة Release / Build Release APK
./gradlew assembleRelease

# موقع الملف / Output location:
# app/build/outputs/apk/release/app-release.apk
```

> **ملاحظة:** يستخدم ملف `release` keystore بيانات debug افتراضية للـ CI.  
> **Note:** The release config uses the debug keystore by default for CI convenience.

---

## 🔐 الأذونات المطلوبة · Permissions Explained

| الإذن / Permission | السبب / Reason |
|---|---|
| `INTERNET` | الاتصال بالراوتر والخوادم / Connect to router & servers |
| `ACCESS_WIFI_STATE` | قراءة معلومات الشبكة / Read network info |
| `ACCESS_FINE_LOCATION` | مطلوب لفحص WiFi في Android 10+ / Required for WiFi scan on Android 10+ |
| `CAMERA` | مسح رموز QR لإعدادات WiFi / Scan QR codes for WiFi config |
| `NFC` | قراءة بطاقات NFC للاتصال السريع / Read NFC tags for quick connect |
| `FOREGROUND_SERVICE` | مراقبة الشبكة المستمرة / Continuous network monitoring |
| `POST_NOTIFICATIONS` | تنبيهات المتطفلين / Intruder alerts |
| `USE_BIOMETRIC` | قفل التطبيق / App lock |

---

## 🛡️ الأمان والامتثال · Safety & Compliance

<table>
<tr>
<td width="50%">

**عربي**

- لا يرسل التطبيق أي بيانات خارج شبكتك المحلية
- يتم تشفير كلمات المرور المحفوظة بـ EncryptedSharedPreferences (AES-256)
- يستخدم TLS لجميع الاتصالات بالإنترنت
- لا يوجد تتبع أو تحليل للبيانات (No Analytics)
- المصدر مفتوح بالكامل — يمكنك مراجعة الكود بنفسك

</td>
<td width="50%">

**English**

- The app sends no data outside your local network
- Saved passwords are encrypted with EncryptedSharedPreferences (AES-256)
- TLS is enforced for all internet-bound connections
- No analytics or telemetry of any kind
- Fully open source — audit the code yourself

</td>
</tr>
</table>

---

## 📸 لقطات الشاشة · Screenshots

> *سيتم إضافة لقطات الشاشة عند اكتمال الواجهة الأمامية.*  
> *Screenshots will be added once the UI is complete.*

| الرئيسية / Dashboard | الأجهزة / Devices | السرعة / Speed | الراوتر / Router |
|---|---|---|---|
| `[placeholder]` | `[placeholder]` | `[placeholder]` | `[placeholder]` |

---

## 🤝 المساهمة · Contributing

نرحب بالمساهمات! / Contributions are welcome!

```bash
# 1. Fork المستودع / Fork the repository
# 2. أنشئ فرعاً / Create a branch
git checkout -b feature/your-feature-name

# 3. طوّر وأضف اختبارات / Develop and add tests
# 4. أرسل Pull Request / Submit a Pull Request
```

**الإرشادات / Guidelines:**
- اتبع Clean Architecture في هيكل الكود / Follow Clean Architecture in code structure
- أضف تعليقات KDoc للوظائف العامة / Add KDoc to public functions
- تأكد من نجاح `./gradlew build` قبل الإرسال / Ensure `./gradlew build` passes before submitting
- استخدم الكود العربي للتعليقات في ملفات UI فقط / Use Arabic comments only in UI files if desired

---

## 📄 الرخصة · License

```
MIT License — Copyright (c) 2026 NetMaster Pro Contributors
```

---

<div align="center">

صُنع بـ ❤️ للمجتمع العربي وما وراءه · Made with ❤️ for the Arabic community and beyond

</div>
