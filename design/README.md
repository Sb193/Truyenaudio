# TruyenAudio · giao diện và nhận diện

Icon được tạo bằng công cụ imagegen tích hợp (không dùng API key hay CLI). Ảnh gốc: `truyenaudio-icon-source.png`. Ảnh được đóng gói vào `android/app/src/main/res/drawable-nodpi/app_brand.png`, các kích thước launcher trong `mipmap-*`, và adaptive icon trong `mipmap-anydpi-v26/ic_launcher.xml`.

Giao diện dùng Jetpack Compose Material 3 và thư viện `androidx.compose.material:material-icons-extended`, tông xanh đêm / xanh ngọc, nền sáng dịu và chế độ tối. Bìa truyện là đồ họa placeholder dựng từ tên truyện; backend không có ảnh bìa. Không thêm dữ liệu truyện mẫu vào luồng sử dụng thực tế.

Ảnh chụp màn hình trong bộ kiểm thử `androidTest` dùng fixture riêng để kiểm tra bố cục, không gọi API production, không nằm trong dữ liệu ứng dụng. Chạy workflow thủ công (Run workflow) để tạo artifact `TruyenAudio-ui-previews`, hoặc push commit có `[ui-preview]`.

## Prompt tạo icon (nguyên văn)

Use case: logo-brand. Asset type: Android app launcher icon for TruyenAudio, a Vietnamese audiobook and story reading app. Create one premium, exceptionally clean square app icon, not a mockup: a single sculptural open book whose outer silhouette smoothly becomes over-ear headphones, with a small subtly integrated play-shaped negative space at the center fold. Elegant soft 3D relief, restrained tactile satin material, luminous mint and teal edges, warm ivory book pages, deep midnight navy full-bleed square background with very subtle teal ambient glow. Bold recognisable silhouette at 48 pixels, perfectly centered, generous safe space around the symbol (all essential symbol within the central 60 percent). Sophisticated contemporary reading and audio brand, crisp edges, no tiny details. Canvas 1024x1024, opaque edge-to-edge square, no rounded outer app tile, no surrounding margins or drop shadow outside canvas. No text, letters, watermark, border, extra icons, devices, or presentation scene.
