# JK Tivi – Android TV (HLS + DASH)

## Điều khiển bằng remote
- Lên / Xuống (hoặc CH+ / CH-): chuyển kênh
- Trái hoặc Menu: mở danh sách kênh. Trong danh sách: Trái/Phải đổi nhóm, Lên/Xuống chọn kênh, OK để phát, Back để đóng
- OK hoặc INFO: hiện thanh thông tin kênh
- Phím số: nhập số kênh (tự chuyển sau 1,5 giây hoặc bấm OK)

## Nguồn phát
Đổi link trong `app/src/main/java/com/example/iptvtv/Playlist.kt` (SOURCE_URL).
Có thể gắn sẵn danh sách trong APK bằng cách đặt file `app/src/main/assets/playlist.m3u`.
Nhóm kênh lấy từ thuộc tính `group-title` của file M3U.

## Build APK trên GitHub
1. Tạo repo mới trên GitHub, đẩy toàn bộ thư mục này lên nhánh `main`.
2. Vào tab Actions, workflow "Build APK" tự chạy (hoặc bấm Run workflow).
3. Xong thì mở lần chạy đó, tải file `JKTivi-apk` ở mục Artifacts.
4. Muốn có link tải cố định: tạo tag `v1.0` và push, APK sẽ nằm ở mục Releases.

## Thông tin kênh
- Khi đổi kênh, app hiện thanh dưới màn hình: logo, số kênh, tên kênh, chương trình đang phát/tiếp theo (EPG), độ phân giải, tốc độ mạng ước lượng và đồng hồ giờ Việt Nam (Asia/Ho_Chi_Minh).
- EPG lấy từ thuộc tính `url-tvg` ở dòng `#EXTM3U` của playlist (định dạng XMLTV, có thể nén gzip). Kênh khớp theo `tvg-id`, nếu không có thì khớp theo tên kênh.
- Logo lấy từ `tvg-logo`, được cache trên đĩa. Ảnh SVG sẽ không hiển thị.
- App nhớ kênh đã xem lần cuối và mở lại đúng kênh đó.

## Giao diện
Phong cách "liquid glass" đen – vàng – trắng: thẻ kính nổi bo góc, viền vàng chuyển sắc, vệt sáng phản chiếu, dòng đang chọn là viên vàng trong suốt, kênh đang phát có số màu vàng. Toàn bộ định nghĩa nằm trong `Glass.kt`.

## Nguồn DASH (.mpd) và header
- Link `.mpd` phát bằng DashMediaSource, link `.m3u8` bằng HlsMediaSource.
- Khi kênh lỗi, mã lỗi hiện ở góc trên bên phải (ví dụ ERROR_CODE_IO_BAD_HTTP_STATUS (HTTP 403)).
- Header riêng cho từng kênh: dùng `#EXTVLCOPT:http-user-agent=...`, `#EXTVLCOPT:http-referrer=...` ngay trên dòng link, hoặc viết `link|User-Agent=...&Referer=...`.
- Chưa hỗ trợ luồng có DRM (Widevine/ClearKey).

## Gợi ý định dạng
Dòng `#KODIPROP:inputstream.adaptive.manifest_type=hls` (hoặc `mpd`) giúp app nhận đúng định dạng khi link không có đuôi .m3u8/.mpd.
Kênh có `license_type` (DRM) sẽ hiện thông báo "không hỗ trợ" thay vì màn hình đen.

## Hàng kênh trên màn hình chính (Projectivy Launcher)
App đăng một hàng "JK Tivi" gồm 12 kênh (VTV1–VTV10, QPVN, ANTV) bằng Android TV Preview Channel. Logo nằm trong `app/src/main/res/drawable-nodpi/`.
Mở app một lần để hàng kênh được tạo, rồi bật hàng đó trong Projectivy (cài đặt hàng/kênh). Chọn một kênh sẽ mở app và phát kênh có tên khớp trong playlist (code ở `HomeChannel.kt`).
