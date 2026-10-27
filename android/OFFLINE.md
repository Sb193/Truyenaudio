# TruyenAudio 2.0 — ưu tiên thư viện trên máy

Mở ứng dụng vào **Trên máy**, không gọi /api/auth/me, không cần tài khoản. Đọc và nghe truyện đã tải chỉ đọc SQLite trên thiết bị, không âm thầm chuyển sang server nếu thiếu chương. Tiến độ nghe local được lưu riêng với cookie/phiên server.

## Sử dụng

1. Mở **Tải thêm**, đăng nhập server bằng tài khoản có sẵn.
2. Chọn truyện → **Tải toàn bộ truyện về máy** → xác nhận. Chỉ tải văn bản, không tạo MP3. Không có chức năng tải khoảng chương hoặc nhập TXT trong bản này.
3. Theo dõi số chương đã lưu. Có thể mở **Trên máy** để nghe các chương đã có. Chỉ khi tiến độ đạt đủ số chương thì toàn bộ truyện mới có sẵn offline.
4. Nếu mất mạng, tác vụ chờ mạng/thử lại có giới hạn. Nếu đã dừng tải hoặc hết phiên, đăng nhập trong Tải thêm nếu cần và chọn tải lại cùng truyện: chương đã lưu được bỏ qua, chỉ lấy phần còn thiếu.
5. Trong Trên máy, chọn giọng offline. Cần cài/tải dữ liệu giọng tiếng Việt của engine trên điện thoại trước khi bật chế độ máy bay. Giọng cần mạng không được dùng cho phiên nghe local.
6. Đăng xuất/đổi tài khoản không xóa thư viện và không dừng phiên nghe local. Đăng xuất dừng nghe server và các tác vụ tải. Chỉ nút **Xóa bản trên máy** mới xóa bản tải của truyện và tiến độ tương ứng trong app.

Gỡ ứng dụng hoặc dùng chức năng Xóa dữ liệu của Android vẫn xóa dữ liệu ứng dụng. Không có đồng bộ hay sao lưu truyện lên cloud. APK debug cần cùng chữ ký để cập nhật giữ dữ liệu; workflow cố giữ khóa debug bằng cache, nhưng cache có thể bị xóa. Muốn phát hành/cập nhật lâu dài nên cấu hình khóa ký riêng ổn định.

## Cấu trúc

- `LocalLibrary`: SQLite có foreign key, lưu từng chương ngay khi tải xong; cập nhật mục lục không xóa các chương đã lưu.
- `ContentRouter`: chỉ khởi tạo nguồn server khi nguồn SERVER được chọn; LOCAL không fallback sang mạng.
- `StoryDownloadWorker`: WorkManager, tải tuần tự toàn bộ số chương từ mục lục thật, bỏ qua chương đã có, thông báo tiến độ và nút dừng. Không giữ cả truyện trong RAM.
- `TtsPlaybackService`: giữ nghe nền/media session/hẹn giờ/chọn phạm vi nghe; nguồn LOCAL không kiểm tra phiên hoặc đọc cookie. Chỉ giữ chương hiện tại và tối đa một chương kế tiếp.
- Phiên Keystore chỉ được khởi tạo khi người dùng vào kho server hoặc tác vụ tải đã được người dùng yêu cầu chạy.

Tác vụ tải dùng foreground dataSync theo WorkManager 2.10.5. Android có thể giới hạn thời gian chạy nền hoặc dừng tác vụ; dữ liệu chương đã ghi vẫn được giữ, chọn tải lại để tiếp tục phần thiếu.

## Xác minh

Unit test: nguồn local không tạo HTTP client, thiếu chương không gọi server, màn hình mặc định không xác thực; cùng các kiểm thử chia văn bản/hàng đợi/cookie/API đã có.

Kiểm thử giả lập: dữ liệu chương giữ sau mở lại kho và xóa phiên; cập nhật mục lục không mất chương; xóa một truyện không ảnh hưởng truyện khác; callback ghi tiến độ muộn không khôi phục tiến độ truyện đã xóa; MainActivity mở ở chế độ local.

Checklist trên Samsung:

- [ ] Tải xong một truyện, bật chế độ máy bay, đóng/mở app, đọc và nghe nhiều chương.
- [ ] Không có màn hình đăng nhập hoặc request server khi mở thư viện local.
- [ ] Đăng xuất/đổi tài khoản vẫn thấy đủ truyện và tiến độ local.
- [ ] Vừa nghe local vừa tải truyện; hết phiên chỉ dừng tải, không ngắt local.
- [ ] Dừng tải giữa chừng, mở lại và tải tiếp không mất chương đã lưu.
- [ ] Tải thêm toàn bộ lần hai không nhân đôi chương; chương cũ giữ nguyên.
- [ ] Khóa màn hình, nhận cuộc gọi, tai nghe và hẹn giờ vẫn hoạt động.
- [ ] Xóa bản local có xác nhận và không gửi lệnh xóa truyện lên server.
