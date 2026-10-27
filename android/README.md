# TruyenAudio Android

Ứng dụng Android native Kotlin + Jetpack Compose, ViewModel + Repository. Android 8.0 trở lên (minSdk 26), target/compile SDK 35, Java 17, AGP 8.8.0, Gradle 8.10.2.

## Build APK

Mở thư mục `android` bằng Android Studio, dùng JDK 17, cài Android SDK Platform 35 và Build Tools 35.0.0. Nếu build từ terminal, cấu hình `ANDROID_HOME` hoặc `local.properties` với `sdk.dir` của máy (không commit file này).

Windows:

```powershell
cd android
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS/Linux:

```sh
cd android
./gradlew testDebugUnitTest assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Đây là APK debug đã ký để cài thử; không phải bản phát hành Play Store. Workflow `.github/workflows/build-apk.yml` tự chạy kiểm thử và build trên mỗi push vào main/master; cũng có nút Run workflow. Sau khi thành công: Actions → lượt chạy → Artifacts → `TruyenAudio-debug-apk` → giải nén ZIP để lấy APK.

## Cài trên Samsung

1. Chép APK vào điện thoại hoặc tải artifact rồi giải nén trên máy tính trước.
2. Mở APK trong My Files. Nếu máy hỏi, cho phép nguồn đang dùng cài ứng dụng không xác định rồi xác nhận cài.
3. Đăng nhập bằng tài khoản backend đang có. Ứng dụng không có chức năng đăng ký.
4. Trong Cài đặt → Quản lý chung → Văn bản thành giọng nói, cài engine/giọng tiếng Việt. Tên menu có thể khác giữa các bản One UI.
5. Cho phép thông báo khi được hỏi để dễ điều khiển. Nếu máy hạn chế phát nền, kiểm tra quyền pin của TruyenAudio và danh sách ứng dụng ngủ trong cài đặt Samsung.
6. Chọn truyện → chương → Đọc hoặc Nghe. Trong cấu hình nghe có phạm vi, engine, voice, tốc độ và hẹn giờ.

Không cần cài APK mới cho mỗi chương. Nếu APK debug lần sau dùng khóa ký khác và Android báo không thể cập nhật, có thể cần gỡ bản debug trước (sẽ mất phiên và tiến độ cục bộ). Workflow dùng khóa debug của môi trường build; chưa thiết lập khóa phát hành ổn định.

## API và phiên

Base URL duy nhất nằm tại `core/constants/AppConstants.kt`: `https://truyenaudio.vip88.workers.dev/`.

- `POST api/auth/login` JSON username/password; nhận user và Set-Cookie, không đòi accessToken.
- `GET api/auth/me`: user=null là hết phiên dù HTTP 200.
- `POST api/auth/logout` JSON `{}`.
- `GET api/stories?page=n`: mỗi trang tối đa 24; dừng khi ít hơn 24. Tìm kiếm chỉ áp dụng danh sách đã tải.
- `GET api/stories/{slug}`: mục lục dùng trường number.
- `GET api/stories/{slug}/chapters/chuong_{number}`: lấy content hoàn chỉnh một chương.

OkHttp native + Retrofit, không WebView, không thêm Origin, không bật HTTP logging. CookieJar chỉ chấp nhận cookie Secure đúng host và chỉ gửi qua HTTPS đúng host; cookie/tiến độ lưu trong EncryptedSharedPreferences với khóa Android Keystore. Không lưu mật khẩu. Tắt redirect HTTP để tránh chuyển request có thông tin đăng nhập sang host khác. Không lưu cache nội dung HTTP trên đĩa. created_at giữ ở JsonElement, không giả định định dạng.

401 từ API được bảo vệ hoặc user=null sẽ xóa phiên/tiến độ, dừng nghe và đưa về đăng nhập. Mất mạng giữ phiên. 429 chỉ hiển thị lỗi chờ, không tự lặp đăng nhập. Đăng xuất dừng dịch vụ và xóa dữ liệu riêng tư cục bộ kể cả khi gọi logout thất bại do mạng; khi đó cookie còn hiệu lực phía server đến khi hết hạn vì không có kết nối để thu hồi.

## Nghe nền và tiến độ

TTS phát trong `TtsPlaybackService`, độc lập Activity/Compose. Foreground service loại mediaPlayback, có notification và MediaSession nhận pause/play/stop/chương trước/sau. Mất audio focus hoặc tháo tai nghe sẽ tạm dừng, không tự phát lại khi cuộc gọi kết thúc. Wake lock có timeout và chỉ lấy khi engine báo bắt đầu phát; giải phóng khi xong đoạn, pause, lỗi, stop hoặc hủy service. Không phát âm thanh im lặng.

Chỉ giữ chương hiện tại và tối đa một chương tải trước, không tải toàn truyện. Chia theo giới hạn `TextToSpeech.getMaxSpeechInputLength()`, ưu tiên câu/đoạn rồi khoảng trắng, không cắt đôi surrogate pair, giữ đúng thứ tự và toàn bộ ký tự. utteranceId theo generation/index loại callback cũ và callback hoàn tất trùng. Chuyển chương theo số trong mục lục, có kiểm tra phạm vi.

Tiến độ lưu trên máy: slug, chương, offset, phạm vi, engine, voice và tốc độ. Khi engine báo onRangeStart, offset được cập nhật định kỳ. Khi tiếp tục, bản hiện tại dùng offset đã lưu; nếu engine không báo vị trí, đọc lại từ đầu đoạn gần nhất để tránh bỏ sót nội dung. Không hiển thị thời lượng audio ước đoán. Force-stop hoặc hệ điều hành chấm dứt tiến trình sẽ dừng phát; mở app để tiếp tục từ tiến độ đã lưu. Hẹn giờ là thời gian đã trôi qua từ lúc bắt đầu phiên nghe, không phải thời lượng audio.

Chọn voice offline ưu tiên vi-VN. Nếu chọn voice cần mạng, phải xác nhận cảnh báo engine có thể gửi nội dung đến nhà cung cấp giọng đọc. Ứng dụng không tích hợp Gemini, analytics hoặc API TTS đám mây riêng; không khẳng định mọi engine xử lý offline.

Tham chiếu triển khai: [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Audio focus](https://developer.android.com/media/optimize/audio-focus), [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech).

## Kiểm thử

Kiểm thử JVM không gọi production: chia văn bản/offset/Unicode/giới hạn nhỏ; callback cũ/trùng/pause/resume/hết hàng đợi; hợp đồng path và JSON đăng nhập/đăng xuất/me/chương/phân trang bằng MockWebServer. Chạy `./gradlew testDebugUnitTest`; báo cáo ở `app/build/reports/tests/testDebugUnitTest/index.html`.

Checklist cần xác nhận trên điện thoại thật với tài khoản của bạn:

- [ ] Đăng nhập đúng/sai; hiện/ẩn mật khẩu; 429 không lặp tự động.
- [ ] Đóng/mở app vẫn giữ phiên; user=null hoặc 401 đưa về đăng nhập.
- [ ] Phân trang hơn 24 truyện, kéo tải lại, tìm chỉ trong truyện đã tải.
- [ ] Mục lục dùng số chương, mở chương trước/sau, cỡ chữ/giãn dòng/dark mode.
- [ ] Nghe toàn bộ, A–B, A–hết; phạm vi sai bị từ chối.
- [ ] Engine không có tiếng Việt hoặc dữ liệu giọng thiếu có thông báo lỗi.
- [ ] Đọc chương dài hơn giới hạn engine; emoji/dấu tiếng Việt không mất nội dung.
- [ ] Tự chuyển nhiều chương đúng phạm vi; stop/đổi truyện khi đang tải không phát callback cũ.
- [ ] Khóa màn hình và chuyển app ít nhất 15 phút, điều khiển bằng notification/màn hình khóa.
- [ ] Pause/play/stop/chương trước/sau từ tai nghe; cuộc gọi và tháo tai nghe tạm dừng.
- [ ] Mất mạng khi tải chương và khi đang đọc bằng voice offline; không mất phiên.
- [ ] Voice cần mạng luôn có cảnh báo/xác nhận; chuyển engine và tốc độ đúng.
- [ ] Hẹn giờ dừng; mở lại app và tiếp tục đúng chương/đoạn gần nhất.
- [ ] Đăng xuất khi đang đọc/tải trước; ngừng âm thanh, xóa phiên/tiến độ và nội dung trên màn hình.
- [ ] Force-stop dừng phát, mở lại app vẫn đọc được tiến độ đã ghi trước đó.

Build thành công không thay thế kiểm thử TTS/Keystore/Samsung trên thiết bị. Không có tài khoản production được tạo hoặc dùng trong kiểm thử tự động.
