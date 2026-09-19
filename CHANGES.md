# SPV Fixed – نسخه کامل (پایداری + ویژگی‌ها)

تاریخ: ۱۴۰۴/۰۶/۲۷

## خلاصه قابلیت‌ها

### پایداری
- Foreground Notification + startForeground
- پرمیشن‌های Android 13/14
- ویجت واقعی با دکمه وصل/قطع
- IPv6 + DNS 1.1.1.1
- درخواست مجوز نوتیفیکیشن

### قابلیت‌ها
1. **جستجو** در کانفیگ‌ها (نام / آدرس / پروتکل / لینک)
2. **Backup / Restore** (JSON در کلیپ‌بورد، شامل لیست Bypass)
3. **Bypass LAN** (سوئیچ)
4. **Split Tunnel واقعی** — انتخاب اپ‌هایی که از VPN رد نشوند (`addDisallowedApplication`)
5. **QR Scanner** داخلی (ZXing) — دکمه «اسکن QR» در دیالوگ افزودن کانفیگ

### وابستگی جدید
- `com.journeyapps:zxing-android-embedded:4.3.0`

## ساخت
```bash
cp /path/to/original/SPV/app/libs/*.aar SPV/app/libs/
echo "sdk.dir=..." > SPV/local.properties
cd SPV && ./gradlew assembleDebug
```

## نکات
- بعد از تغییر لیست Bypass یا سوئیچ LAN، یک بار قطع و وصل کنید تا اعمال شود.
- برای QR به مجوز دوربین نیاز است.
- Room هنوز پیاده نشده (SharedPreferences کافی برای حجم متوسط است).

## کانال
https://t.me/spiderV2ray
